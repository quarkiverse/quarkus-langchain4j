package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.runtime.tool.ToolExecutionErrorContext;
import io.quarkiverse.langchain4j.runtime.tool.ToolExecutionRequestContext;
import io.quarkiverse.langchain4j.runtime.tool.ToolExecutionResponseContext;
import io.quarkiverse.langchain4j.runtime.tool.ToolSpanContributor;
import io.quarkiverse.langchain4j.runtime.tool.ToolSpanWrapper;
import io.quarkus.arc.All;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorToolSpanWrapperTest {
    @Inject
    ToolSpanWrapper toolSpanWrapper;

    @Inject
    @All
    List<ToolSpanContributor> contributors;

    @Inject
    InMemorySpanExporter exporter;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            # Since using a InMemorySpanExporter inside our tests,
                            # these properties reduce the export timeout and schedule delay from the BatchSpanProcessor
                            quarkus.otel.bsp.schedule.delay=PT0.001S
                            quarkus.otel.bsp.max.queue.size=1
                            quarkus.otel.bsp.max.export.batch.size=1
                            """), "application.properties")
                    .addClasses(InMemorySpanExporterProducer.class));

    @BeforeEach
    void cleanupSpans() {
        exporter.reset();
    }

    @Test
    void shouldCreateSpanOnSuccess() {
        var ctx = MockedContexts.create();

        toolSpanWrapper.wrap(
                ctx.requestContext().request(),
                ctx.requestContext().invocationContext(),
                (request, invocationContext) -> ctx.responseContext().result(),
                null);

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        var actualSpan = exporter.getFinishedSpanItems().get(0);
        assertThat(actualSpan.getName()).isEqualTo("langchain4j.tools.toolName");
    }

    @Test
    void shouldCreateAndEndSpanOnFailure() {
        var ctx = MockedContexts.create();

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> toolSpanWrapper.wrap(
                        ctx.requestContext().request(),
                        ctx.requestContext().invocationContext(),
                        (request, invocationContext) -> {
                            throw (RuntimeException) ctx.errorContext().error();
                        },
                        null))
                .withMessage(ctx.errorContext().error().getMessage());

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        var actualSpan = exporter.getFinishedSpanItems().get(0);
        assertThat(actualSpan.getName()).isEqualTo("langchain4j.tools.toolName");
        assertThat(actualSpan.getEvents())
                .hasSize(1)
                .first()
                .extracting("exception")
                .extracting("message")
                .isEqualTo("--failed--");
        verifyFailedSpan(actualSpan);
    }

    protected void verifyFailedSpan(SpanData actualSpan) {
        // nothing here
    }

    public static class InMemorySpanExporterProducer {
        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }

    @ApplicationScoped
    public static class TestSpanContributor implements ToolSpanContributor {
        private final MockedContexts contexts = MockedContexts.create();

        @Override
        public void onRequest(ToolExecutionRequestContext context, Span span) {
            assertThat(context)
                    .usingRecursiveComparison()
                    .ignoringFields("attributes")
                    .isEqualTo(contexts.requestContext());
        }

        @Override
        public void onResponse(ToolExecutionResponseContext context, Span span) {
            assertThat(context)
                    .usingRecursiveComparison()
                    .ignoringFields("requestContext.attributes")
                    .isEqualTo(contexts.responseContext());
        }

        @Override
        public void onError(ToolExecutionErrorContext context, Span span) {
            assertThat(context).isNotNull();

            assertThat(context.requestContext())
                    .usingRecursiveComparison()
                    .ignoringFields("attributes")
                    .isEqualTo(contexts.errorContext().requestContext());

            assertThat(context.error())
                    .hasMessage(contexts.errorContext().error().getMessage());
        }
    }

    record MockedContexts(
            ToolExecutionRequestContext requestContext,
            ToolExecutionResponseContext responseContext,
            ToolExecutionErrorContext errorContext) {

        static MockedContexts create() {
            var request = ToolExecutionRequest.builder()
                    .id("id")
                    .name("toolName")
                    .arguments("args")
                    .build();

            var invocationContext = InvocationContext.builder().build();
            var response = ToolExecutionResult.builder()
                    .resultText("result")
                    .build();

            var requestCtx = ToolExecutionRequestContext.builder()
                    .request(request)
                    .invocationContext(invocationContext)
                    .build();

            var responseCtx = ToolExecutionResponseContext.builder()
                    .requestContext(requestCtx)
                    .result(response)
                    .build();

            var errorCtx = ToolExecutionErrorContext.builder()
                    .requestContext(requestCtx)
                    .error(new RuntimeException("--failed--"))
                    .build();

            return new MockedContexts(requestCtx, responseCtx, errorCtx);
        }
    }
}
