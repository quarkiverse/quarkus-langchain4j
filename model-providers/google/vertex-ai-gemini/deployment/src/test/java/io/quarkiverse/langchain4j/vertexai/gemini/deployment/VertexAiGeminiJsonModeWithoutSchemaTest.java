package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

public class VertexAiGeminiJsonModeWithoutSchemaTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";

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
        mock.chat().reply(ChatReply.text("{\"city\":\"Paris\"}").usage(1, 1));
    }

    @Test
    void jsonResponseFormatWithoutSchemaIsSentAsJson() {
        chatModel.chat(request(ResponseFormat.JSON));

        assertJsonModeWithoutSchema(sentGenerationConfig());
    }

    @Test
    void streamingJsonResponseFormatWithoutSchemaIsSentAsJson() throws Exception {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat(request(ResponseFormat.JSON), new StreamingChatResponseHandler() {
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
        response.get(30, TimeUnit.SECONDS);

        assertJsonModeWithoutSchema(sentGenerationConfig());
    }

    @Test
    void textResponseFormatIsSentAsPlainText() {
        chatModel.chat(request(ResponseFormat.TEXT));

        assertThat(sentGenerationConfig().get("responseMimeType").asString()).isEqualTo("text/plain");
    }

    private static void assertJsonModeWithoutSchema(JsonNode generationConfig) {
        assertThat(generationConfig.get("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(generationConfig.get("responseSchema")).isNull();
        assertThat(generationConfig.get("responseJsonSchema")).isNull();
    }

    private static ChatRequest request(ResponseFormat responseFormat) {
        return ChatRequest.builder()
                .messages(UserMessage.from("Which city is the capital of France? Answer in JSON."))
                .responseFormat(responseFormat)
                .build();
    }

    private JsonNode sentGenerationConfig() {
        return mock.chat().singleRequest().bodyAsJson().get("generationConfig");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
