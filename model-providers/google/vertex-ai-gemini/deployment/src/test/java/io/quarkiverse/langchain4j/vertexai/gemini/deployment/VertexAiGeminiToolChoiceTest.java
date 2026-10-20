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
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiToolChoiceTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final ToolSpecification GET_WEATHER = ToolSpecification.builder()
            .name("getWeather").description("Returns the weather").build();

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
        mock.chat().reply("Hi");
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
    void noToolConfigIsSentWithoutToolChoice() {
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

    private JsonNode sentToolConfig() {
        return mock.chat().singleRequest().bodyAsJson().get("toolConfig");
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
