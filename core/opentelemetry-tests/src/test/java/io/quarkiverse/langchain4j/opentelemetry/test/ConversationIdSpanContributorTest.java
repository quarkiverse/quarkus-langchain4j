package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;
import io.quarkiverse.langchain4j.runtime.listeners.ConversationIdSpanContributor;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;

class ConversationIdSpanContributorTest extends ListenersProcessorAbstractSpanChatModelListenerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> appWithInMemorySpanExporter());

    @AfterEach
    void cleanupConversation() {
        // ConversationContext uses ContextLocals which may not be active here, but end() is safe
        try {
            ConversationContext.end();
        } catch (Exception ignored) {
        }
    }

    @Test
    void shouldHaveConversationIdSpanContributor() {
        assertThat(contributors).anyMatch(c -> c instanceof ConversationIdSpanContributor);
    }

    @Test
    @RunOnVertxContext
    void shouldSetConversationIdOnSpanWhenActive(UniAsserter asserter) {
        asserter.execute(() -> {
            ConversationContext.begin("test-conv-123");
            var ctx = MockedContexts.create();
            spanChatModelListener.onRequest(ctx.requestContext());
            spanChatModelListener.onResponse(ctx.responseContext());
        });
        asserter.execute(() -> {
            await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.conversation.id")))
                    .isEqualTo("test-conv-123");
            ConversationContext.end();
        });
    }

    @Test
    void shouldNotSetConversationIdWhenNotActive() {
        var ctx = MockedContexts.create();
        spanChatModelListener.onRequest(ctx.requestContext());
        spanChatModelListener.onResponse(ctx.responseContext());

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.conversation.id")))
                .isNull();
    }
}
