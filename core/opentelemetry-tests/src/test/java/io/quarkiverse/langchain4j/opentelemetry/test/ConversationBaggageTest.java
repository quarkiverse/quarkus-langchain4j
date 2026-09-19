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
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that incoming OTel Baggage with {@code gen_ai.conversation.id} is picked up
 * by the span contributor as a fallback when no local conversation is active.
 */
class ConversationBaggageTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> {
                var applicationProperties = """
                        quarkus.otel.bsp.schedule.delay=PT0.001S
                        quarkus.otel.bsp.max.queue.size=1
                        quarkus.otel.bsp.max.export.batch.size=1
                        """;
                return ShrinkWrap.create(JavaArchive.class)
                        .addAsResource(new StringAsset(applicationProperties), "application.properties")
                        .addClasses(InMemorySpanExporterProducer.class);
            });

    @Inject
    InMemorySpanExporter exporter;

    @Inject
    SpanChatModelListener spanChatModelListener;

    @BeforeEach
    void resetSpans() {
        exporter.reset();
    }

    @Test
    void incomingBaggageSetsSpanAttribute() {
        Scope baggageScope = Baggage.builder()
                .put("gen_ai.conversation.id", "upstream-conv-999")
                .build()
                .storeInContext(Context.current())
                .makeCurrent();
        try {
            var attributes = new HashMap<>();
            var request = ChatRequest.builder().messages(List.of(UserMessage.from("--test--")))
                    .parameters(DefaultChatRequestParameters.builder().modelName("--mock--")
                            .temperature(0.0).topP(0.0).build())
                    .build();
            var response = ChatResponse.builder().aiMessage(AiMessage.from("--response--")).build();
            var requestCtx = new ChatModelRequestContext(request, ModelProvider.OTHER, attributes);
            var responseCtx = new ChatModelResponseContext(response, request, ModelProvider.OTHER, attributes);

            spanChatModelListener.onRequest(requestCtx);
            spanChatModelListener.onResponse(responseCtx);

            await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
            assertThat(exporter.getFinishedSpanItems().get(0).getAttributes()
                    .get(AttributeKey.stringKey("gen_ai.conversation.id")))
                    .isEqualTo("upstream-conv-999");
        } finally {
            baggageScope.close();
        }
    }

    @Test
    void noBaggageNoAttribute() {
        var attributes = new HashMap<>();
        var request = ChatRequest.builder().messages(List.of(UserMessage.from("--test--")))
                .parameters(DefaultChatRequestParameters.builder().modelName("--mock--")
                        .temperature(0.0).topP(0.0).build())
                .build();
        var response = ChatResponse.builder().aiMessage(AiMessage.from("--response--")).build();
        var requestCtx = new ChatModelRequestContext(request, ModelProvider.OTHER, attributes);
        var responseCtx = new ChatModelResponseContext(response, request, ModelProvider.OTHER, attributes);

        spanChatModelListener.onRequest(requestCtx);
        spanChatModelListener.onResponse(responseCtx);

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        assertThat(exporter.getFinishedSpanItems().get(0).getAttributes()
                .get(AttributeKey.stringKey("gen_ai.conversation.id")))
                .isNull();
    }

    @ApplicationScoped
    public static class InMemorySpanExporterProducer {
        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }
}
