package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@code chat-model.api-key} and {@code chat-model.api-version} override the top-level values for both chat models,
 * the streaming one included.
 */
public class AzureOpenAiChatModelOverridesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "top-level-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.api-key", "chat-model-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.api-version", "2025-04-01-preview");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void chatModelUsesTheChatModelOverrides() {
        mock.chat().reply("ok");

        assertThat(chatModel.chat("hi")).isEqualTo("ok");

        assertOverridesUsed(mock.chat().singleRequest());
    }

    @Test
    void streamingChatModelUsesTheChatModelOverrides() throws Exception {
        mock.chat().reply(ChatReply.text("ok").chunks(List.of("o", "k")));

        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }
        });
        assertThat(response.get(1, TimeUnit.MINUTES).aiMessage().text()).isEqualTo("ok");

        assertOverridesUsed(mock.chat().singleRequest());
    }

    private static void assertOverridesUsed(CapturedRequest request) {
        assertThat(request.header("api-key")).isEqualTo("chat-model-key");
        assertThat(request.query()).contains("api-version=2025-04-01-preview");
    }
}
