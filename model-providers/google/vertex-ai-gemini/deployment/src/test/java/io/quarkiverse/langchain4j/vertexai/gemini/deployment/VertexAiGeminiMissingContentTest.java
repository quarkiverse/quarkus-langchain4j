package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Every response here is a raw stub: a blocked prompt and a candidate without parts are Gemini response shapes the
 * neutral reply does not express.
 */
public class VertexAiGeminiMissingContentTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String MODEL_PATH = "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/"
            + CHAT_MODEL_ID;
    private static final String BLOCKED_PROMPT = """
            {"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"},
             "usageMetadata":{"promptTokenCount":1,"totalTokenCount":1}}""";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void blockedPromptReportsBlockReason() {
        mock.raw().on("POST", MODEL_PATH + ":generateContent").reply(200, BLOCKED_PROMPT);

        assertThatThrownBy(() -> chatModel.chat(UserMessage.from("hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROHIBITED_CONTENT");

        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo(MODEL_PATH + ":generateContent"));
    }

    @Test
    void blockedStreamedPromptFinishesWithContentFilter() throws Exception {
        mock.raw().on("POST", MODEL_PATH + ":streamGenerateContent")
                .replies(RawResponse.of(200, Map.of("Content-Type", "text/event-stream"),
                        "data: " + BLOCKED_PROMPT.replace("\n", "") + "\n\n"));

        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        });

        assertThat(response.get(30, TimeUnit.SECONDS).finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo(MODEL_PATH + ":streamGenerateContent"));
    }

    @Test
    void candidateWithoutPartsIsNotAnError() {
        mock.raw().on("POST", MODEL_PATH + ":generateContent").reply(200, """
                {"candidates":[{"content":{"role":"model"},"finishReason":"MAX_TOKENS"}],
                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":0,"totalTokenCount":1}}
                """);

        ChatResponse response = chatModel.chat(UserMessage.from("hello"));

        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(response.aiMessage().hasToolExecutionRequests()).isFalse();
        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo(MODEL_PATH + ":generateContent"));
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
