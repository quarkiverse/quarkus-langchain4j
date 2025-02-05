package io.quarkiverse.langchain4j.test.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.runtime.listeners.ChatModelSpanContributor;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorSingleChatModelSpanContributorTest
        extends ListenersProcessorAbstractSpanChatModelListenerTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> appWithInMemorySpanExporter().addClasses(TestChatModelSpanContributor.class))
            .setForcedDependencies(
                    List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry", "3.15.2")));

    @Test
    void shouldHaveSpanChatModelListenerWitContributor() {
        assertThat(spanChatModelListener).isNotNull();
        assertThat(contributors).hasSize(1).first().isInstanceOf(TestChatModelSpanContributor.class);
    }

    @Override
    protected void verifySuccessfulSpan(SpanData actualSpan) {
        assertThat(actualSpan.getAttributes().get(AttributeKey.stringKey(("--custom-on-request--"))))
                .isEqualTo("--value-on-request--");
        assertThat(actualSpan.getAttributes().get(AttributeKey.stringKey(("--custom-on-response--"))))
                .isEqualTo("--value-on-response--");
        assertThat(actualSpan.getAttributes().get(AttributeKey.stringKey(("--custom-on-error--"))))
                .isNull();
    }

    @Override
    protected void verifyFailedSpan(SpanData actualSpan) {
        assertThat(actualSpan.getAttributes().get(AttributeKey.stringKey(("--custom-on-request--"))))
                .isEqualTo("--value-on-request--");
        assertThat(actualSpan.getAttributes().get(AttributeKey.stringKey(("--custom-on-response--"))))
                .isNull();
        assertThat(actualSpan.getAttributes().get(AttributeKey.stringKey(("--custom-on-error--"))))
                .isEqualTo("--value-on-error--");
    }

    @ApplicationScoped
    public static class TestChatModelSpanContributor implements ChatModelSpanContributor {
        @Override
        public void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {
            currentSpan.setAttribute("--custom-on-request--", "--value-on-request--");
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext, Span currentSpan) {
            currentSpan.setAttribute("--custom-on-response--", "--value-on-response--");
        }

        @Override
        public void onError(ChatModelErrorContext errorContext, Span currentSpan) {
            currentSpan.setAttribute("--custom-on-error--", "--value-on-error--");
        }
    }
}
