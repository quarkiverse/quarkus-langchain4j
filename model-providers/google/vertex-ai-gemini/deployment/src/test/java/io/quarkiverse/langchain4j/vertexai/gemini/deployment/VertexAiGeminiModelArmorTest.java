package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiModelArmorTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String MODEL_URL = "/v1/projects/dummy/locations/dummy/publishers/google/models/" + CHAT_MODEL_ID;
    private static final String PROMPT_TEMPLATE = "projects/p/locations/europe-west4/templates/prompt";
    private static final String RESPONSE_TEMPLATE = "projects/p/locations/europe-west4/templates/response";
    private static final String OK_RESPONSE = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Hi"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""";
    private static final String BLOCKED_RESPONSE = """
            {"candidates":[{"finishReason":"MODEL_ARMOR","finishMessage":"Blocked by Model Armor"}],
             "usageMetadata":{"promptTokenCount":1,"totalTokenCount":1}}""";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-armor.prompt-template-name",
                    PROMPT_TEMPLATE)
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-armor.response-template-name",
                    RESPONSE_TEMPLATE);

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void reset() {
        resetRequests();
        resetMappings();
    }

    @Test
    void requestCarriesModelArmorConfig() throws Exception {
        stubGenerateContent(OK_RESPONSE);

        chatModel.chat("hello");

        JsonNode modelArmorConfig = requestJson().get("modelArmorConfig");
        assertThat(modelArmorConfig.get("promptTemplateName").asText()).isEqualTo(PROMPT_TEMPLATE);
        assertThat(modelArmorConfig.get("responseTemplateName").asText()).isEqualTo(RESPONSE_TEMPLATE);
    }

    @Test
    void streamingRequestCarriesModelArmorConfig() throws Exception {
        stubStreamGenerateContent(OK_RESPONSE);

        streamChat();

        JsonNode modelArmorConfig = requestJson().get("modelArmorConfig");
        assertThat(modelArmorConfig.get("promptTemplateName").asText()).isEqualTo(PROMPT_TEMPLATE);
        assertThat(modelArmorConfig.get("responseTemplateName").asText()).isEqualTo(RESPONSE_TEMPLATE);
    }

    @Test
    void blockedPromptReportsBlockReasonMessage() {
        stubGenerateContent("""
                {"promptFeedback":{"blockReason":"MODEL_ARMOR","blockReasonMessage":"Blocked by Model Armor Floor Setting"},
                 "usageMetadata":{"promptTokenCount":1,"totalTokenCount":1}}""");

        assertThatThrownBy(() -> chatModel.chat(UserMessage.from("hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The prompt is blocked with reason MODEL_ARMOR. Blocked by Model Armor Floor Setting");
    }

    @Test
    void blockedResponseIsReported() {
        stubGenerateContent(BLOCKED_RESPONSE);

        assertThatThrownBy(() -> chatModel.chat(UserMessage.from("hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The response is blocked by Model Armor.");
    }

    @Test
    void blockedStreamedResponseFinishesWithContentFilter() throws Exception {
        stubStreamGenerateContent(BLOCKED_RESPONSE);

        assertThat(streamChat().finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
    }

    @Test
    void unknownFinishReasonMapsToOther() {
        stubGenerateContent("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Hi"}]},"finishReason":"SOMETHING_NEW"}],
                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""");

        ChatResponse response = chatModel.chat(UserMessage.from("hello"));

        assertThat(response.aiMessage().text()).isEqualTo("Hi");
        assertThat(response.finishReason()).isEqualTo(FinishReason.OTHER);
    }

    private void stubGenerateContent(String body) {
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":generateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void stubStreamGenerateContent(String body) {
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":streamGenerateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
                        .withBody("data: " + body.replace("\n", "") + "\n\n")));
    }

    private JsonNode requestJson() throws Exception {
        return new ObjectMapper().readTree(requestBodyOfSingleRequest());
    }

    private ChatResponse streamChat() throws Exception {
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
        return response.get(30, TimeUnit.SECONDS);
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
