package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.HashMap;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.runtime.listeners.ChatModelSpanContributor;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.arc.All;

abstract class ListenersProcessorAbstractSpanChatModelListenerTest {
    @Inject
    SpanChatModelListener spanChatModelListener;
    @Inject
    @All
    List<ChatModelSpanContributor> contributors;
    @Inject
    InMemorySpanExporter exporter;

    static JavaArchive appWithInMemorySpanExporter() {
        var applicationProperties = """
                # Since using a InMemorySpanExporter inside our tests,
                # these properties reduce the export timeout and schedule delay from the BatchSpanProcessor.
                # The queue size is left at its default: a queue of 1 drops spans when several end
                # back-to-back on the same thread, e.g. the turn span and the span of the work
                # following the chat in ListenersProcessorStreamingSpanChatModelListenerTest.
                quarkus.otel.bsp.schedule.delay=PT0.001S
                quarkus.otel.bsp.max.export.batch.size=1
                """;
        return ShrinkWrap.create(JavaArchive.class)
                .addAsResource(new StringAsset(applicationProperties), "application.properties")
                .addClasses(InMemorySpanExporterProducer.class);
    }

    @BeforeEach
    void cleanupSpans() {
        exporter.reset();
    }

    @Test
    void shouldCreateOpeningAndClosingSpansOnSuccess() {
        var ctx = MockedContexts.create();

        spanChatModelListener.onRequest(ctx.requestContext());
        spanChatModelListener.onResponse(ctx.responseContext());

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(2));
        SpanData openingSpan = span("chat --mock-model-name-- open");
        SpanData closingSpan = span("chat --mock-model-name-- close");
        assertThat(closingSpan.getParentSpanId()).isEqualTo(openingSpan.getSpanId());
        verifySuccessfulSpan(openingSpan, closingSpan);
    }

    @Test
    void shouldCreateOpeningAndClosingSpansOnFailure() {
        var ctx = MockedContexts.create();

        spanChatModelListener.onRequest(ctx.requestContext());
        spanChatModelListener.onError(ctx.errorContext());

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(2));
        SpanData openingSpan = span("chat --mock-model-name-- open");
        SpanData closingSpan = span("chat --mock-model-name-- close");
        assertThat(closingSpan.getParentSpanId()).isEqualTo(openingSpan.getSpanId());
        assertThat(closingSpan.getEvents())
                .hasSize(1)
                .first()
                .extracting("exception")
                .extracting("message")
                .isEqualTo("--failed--");
        verifyFailedSpan(openingSpan, closingSpan);
    }

    protected void verifySuccessfulSpan(SpanData openingSpan, SpanData closingSpan) {
        // nothing here
    }

    protected void verifyFailedSpan(SpanData openingSpan, SpanData closingSpan) {
        // nothing here
    }

    protected SpanData span(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    public static class InMemorySpanExporterProducer {
        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }

    record MockedContexts(
            ChatModelRequestContext requestContext,
            ChatModelResponseContext responseContext,
            ChatModelErrorContext errorContext) {
        static MockedContexts create() {
            var attributes = new HashMap<>();
            var request = ChatRequest.builder().messages(List.of(UserMessage.from("--test-message--")))
                    .parameters(DefaultChatRequestParameters.builder().modelName("--mock-model-name--").temperature(0.0)
                            .topP(0.0).build())
                    .build();
            var response = ChatResponse.builder().aiMessage(AiMessage.from("--test-response--")).build();
            var requestCtx = new ChatModelRequestContext(request, ModelProvider.OTHER, attributes);
            var responseContext = new ChatModelResponseContext(response, request, ModelProvider.OTHER, attributes);
            var errorCtx = new ChatModelErrorContext(
                    new RuntimeException("--failed--"), request, ModelProvider.OTHER, attributes);
            return new MockedContexts(requestCtx, responseContext, errorCtx);
        }
    }
}
