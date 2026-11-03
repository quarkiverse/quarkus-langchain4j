package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiJsonModeWithoutSchemaTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String MODEL_URL = "/v1/projects/dummy/locations/dummy/publishers/google/models/" + CHAT_MODEL_ID;
    private static final String RESPONSE = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"city\\":\\"Paris\\"}"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void setUp() {
        resetRequests();
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":generateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(RESPONSE)));
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":streamGenerateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
                        .withBody("data: " + RESPONSE.replace("\n", "") + "\n\n")));
    }

    @Test
    void jsonResponseFormatWithoutSchemaIsSentAsJson() throws Exception {
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
    void textResponseFormatIsSentAsPlainText() throws Exception {
        chatModel.chat(request(ResponseFormat.TEXT));

        assertThat(sentGenerationConfig().get("responseMimeType").asText()).isEqualTo("text/plain");
    }

    private static void assertJsonModeWithoutSchema(JsonNode generationConfig) {
        assertThat(generationConfig.get("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(generationConfig.get("responseSchema")).isNull();
        assertThat(generationConfig.get("responseJsonSchema")).isNull();
    }

    private static ChatRequest request(ResponseFormat responseFormat) {
        return ChatRequest.builder()
                .messages(UserMessage.from("Which city is the capital of France? Answer in JSON."))
                .responseFormat(responseFormat)
                .build();
    }

    private JsonNode sentGenerationConfig() throws Exception {
        return new ObjectMapper().readTree(singleLoggedRequest().getBodyAsString()).get("generationConfig");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
