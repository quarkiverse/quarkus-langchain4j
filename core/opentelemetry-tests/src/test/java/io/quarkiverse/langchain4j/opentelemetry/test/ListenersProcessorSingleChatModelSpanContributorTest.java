package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorSingleChatModelSpanContributorTest
        extends ListenersProcessorAbstractSpanChatModelListenerTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> appWithInMemorySpanExporter().addClasses(TestChatModelSpanContributor.class));

    @Test
    void shouldHaveSpanChatModelListenerWitContributor() {
        assertThat(spanChatModelListener).isNotNull();
        assertThat(contributors).hasSize(3).anyMatch(i -> i instanceof TestChatModelSpanContributor);
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
