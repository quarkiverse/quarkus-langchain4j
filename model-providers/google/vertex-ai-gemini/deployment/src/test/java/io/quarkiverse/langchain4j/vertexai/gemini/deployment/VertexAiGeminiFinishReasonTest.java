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

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiFinishReasonTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String MODEL_URL = "/v1/projects/dummy/locations/dummy/publishers/google/models/" + CHAT_MODEL_ID;

    private static final String TOOL_CALL_RESPONSE = """
            {"candidates":[{"content":{"role":"model","parts":[
                {"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"}
             ]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
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
    void toolCallTurnReportsToolExecution() {
        stub(TOOL_CALL_RESPONSE);

        ChatResponse response = chatModel.chat(UserMessage.from("What is the weather in Paris?"));

        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    private void stub(String body) {
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":generateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
