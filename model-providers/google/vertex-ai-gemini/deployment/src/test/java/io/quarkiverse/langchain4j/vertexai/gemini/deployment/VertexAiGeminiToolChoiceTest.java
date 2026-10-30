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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiToolChoiceTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String MODEL_URL = "/v1/projects/dummy/locations/dummy/publishers/google/models/" + CHAT_MODEL_ID;
    private static final String RESPONSE = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Hi"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""";
    private static final ToolSpecification GET_WEATHER = ToolSpecification.builder()
            .name("getWeather").description("Returns the weather").build();

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
    void requiredToolChoiceIsSentAsModeAny() throws Exception {
        chatModel.chat(request(ToolChoice.REQUIRED));

        assertThat(sentToolConfig()).isEqualTo(json("""
                {"functionCallingConfig":{"mode":"ANY"}}"""));
    }

    @Test
    void noneToolChoiceIsSentAsModeNone() throws Exception {
        chatModel.chat(request(ToolChoice.NONE));

        assertThat(sentToolConfig()).isEqualTo(json("""
                {"functionCallingConfig":{"mode":"NONE"}}"""));
    }

    @Test
    void noToolConfigIsSentWithoutToolChoice() throws Exception {
        chatModel.chat(request(null));

        assertThat(sentToolConfig()).isNull();
    }

    @Test
    void streamingSendsToolChoice() throws Exception {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat(request(ToolChoice.REQUIRED), new StreamingChatResponseHandler() {
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

        assertThat(sentToolConfig()).isEqualTo(json("""
                {"functionCallingConfig":{"mode":"ANY"}}"""));
    }

    private static ChatRequest request(ToolChoice toolChoice) {
        return ChatRequest.builder()
                .messages(UserMessage.from("What is the weather in Paris?"))
                .toolSpecifications(GET_WEATHER)
                .toolChoice(toolChoice)
                .build();
    }

    private JsonNode sentToolConfig() throws Exception {
        return json(singleLoggedRequest().getBodyAsString()).get("toolConfig");
    }

    private static JsonNode json(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
