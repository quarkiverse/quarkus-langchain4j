package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiToolTurnOrderTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void setUp() {
        resetRequests();
        wiremock().register(
                post(urlPathEqualTo(String.format(
                        "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:generateContent", CHAT_MODEL_ID)))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {"candidates":[{"content":{"role":"model","parts":[{"text":"Dry in both."}]},"finishReason":"STOP"}],
                                                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}}
                                                """)));
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

    private JsonNode sentContents() throws Exception {
        return MAPPER.readTree(singleLoggedRequest().getBodyAsString()).get("contents");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
