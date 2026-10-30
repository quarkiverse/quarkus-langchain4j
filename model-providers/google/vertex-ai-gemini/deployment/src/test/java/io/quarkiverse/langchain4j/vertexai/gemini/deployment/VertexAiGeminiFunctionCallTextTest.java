package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiFunctionCallTextTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";

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

    private AiMessage chat(String parts) {
        wiremock().register(
                post(urlPathEqualTo(String.format(
                        "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:generateContent", CHAT_MODEL_ID)))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"candidates":[{"content":{"role":"model","parts":[%s]},"finishReason":"STOP"}],
                                         "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}}
                                        """.formatted(parts))));

        return chatModel.chat(UserMessage.from("What is the weather in Paris?")).aiMessage();
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
