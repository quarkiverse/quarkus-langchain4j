package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiFunctionCallTextTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String CHAT_PATH = String.format(
            "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/%s:generateContent",
            CHAT_MODEL_ID);

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
    }

    @Test
    void functionCallOnlyResponseHasNoText() {
        AiMessage aiMessage = chat("""
                {"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"},
                {"functionCall":{"name":"getTime","args":{"city":"Paris"}}}""");

        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests())
                .extracting(ToolExecutionRequest::name)
                .containsExactly("getWeather", "getTime");
    }

    @Test
    void textBeforeFunctionCallIsKept() {
        AiMessage aiMessage = chat("""
                {"text":"Let me check the weather."},
                {"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"}""");

        assertThat(aiMessage.text()).isEqualTo("Let me check the weather.");
        assertThat(aiMessage.toolExecutionRequests())
                .extracting(ToolExecutionRequest::name)
                .containsExactly("getWeather");
    }

    // a raw stub, because the thought signature on a function call part is a Gemini detail the neutral reply does not express
    private AiMessage chat(String parts) {
        mock.raw().on("POST", CHAT_PATH).reply(200, """
                {"candidates":[{"content":{"role":"model","parts":[%s]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}}
                """.formatted(parts));

        AiMessage aiMessage = chatModel.chat(UserMessage.from("What is the weather in Paris?")).aiMessage();

        assertThat(mock.requests()).singleElement().satisfies(request -> assertThat(request.path()).isEqualTo(CHAT_PATH));
        return aiMessage;
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
