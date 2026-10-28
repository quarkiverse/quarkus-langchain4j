package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiToolTurnOrderTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String CHAT_PATH = String.format(
            "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/%s:generateContent",
            CHAT_MODEL_ID);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        mock.chat().reply(ChatReply.text("Dry in both.").usage(1, 2));
    }

    @Test
    void parallelToolCallsAreSentAsOneTurnInConversationOrder() throws Exception {
        ToolExecutionRequest paris = ToolExecutionRequest.builder()
                .id("signature-1").name("getWeather").arguments("{\"city\":\"Paris\"}").build();
        ToolExecutionRequest london = ToolExecutionRequest.builder()
                .name("getWeather").arguments("{\"city\":\"London\"}").build();

        chatModel.chat(
                UserMessage.from("What is the weather in Paris and London?"),
                AiMessage.from(paris, london),
                ToolExecutionResultMessage.from(paris, "sunny"),
                ToolExecutionResultMessage.from(london, "rainy"),
                AiMessage.from("Sunny in Paris, rainy in London."),
                UserMessage.from("Will it stay dry tomorrow?"));

        assertThat(sentContents()).isEqualTo(MAPPER.readTree("""
                [
                  {"role":"user","parts":[{"text":"What is the weather in Paris and London?"}]},
                  {"role":"model","parts":[
                    {"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"},
                    {"functionCall":{"name":"getWeather","args":{"city":"London"}}}]},
                  {"role":"user","parts":[
                    {"functionResponse":{"name":"getWeather","response":{"name":"getWeather","content":"sunny"}}},
                    {"functionResponse":{"name":"getWeather","response":{"name":"getWeather","content":"rainy"}}}]},
                  {"role":"model","parts":[{"text":"Sunny in Paris, rainy in London."}]},
                  {"role":"user","parts":[{"text":"Will it stay dry tomorrow?"}]}
                ]
                """));
    }

    @Test
    void textOfAiMessageWithToolCallsIsSentBeforeTheCalls() throws Exception {
        ToolExecutionRequest paris = ToolExecutionRequest.builder()
                .id("signature-1").name("getWeather").arguments("{\"city\":\"Paris\"}").build();

        chatModel.chat(
                UserMessage.from("What is the weather in Paris?"),
                AiMessage.from("Let me check the weather.", List.of(paris)),
                ToolExecutionResultMessage.from(paris, "sunny"));

        assertThat(sentContents().get(1)).isEqualTo(MAPPER.readTree("""
                {"role":"model","parts":[
                  {"text":"Let me check the weather."},
                  {"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"}]}
                """));
    }

    private JsonNode sentContents() {
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo(CHAT_PATH);
        return request.bodyAsJson().get("contents");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
