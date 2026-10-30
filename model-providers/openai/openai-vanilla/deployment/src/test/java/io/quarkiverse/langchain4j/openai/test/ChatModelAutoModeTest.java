package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies how {@code quarkus.langchain4j.openai.chat-model.mode} is resolved when it is not set explicitly:
 * the Responses API is selected when talking to OpenAI itself, any other base URL keeps using Chat Completions.
 * An explicit {@code mode} always wins and is a runtime property.
 */
public class ChatModelAutoModeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            // OpenAI base URL, default model -> responses
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-default.api-key", "key")
            // OpenAI base URL, pre-GPT-6 model -> still responses, the model name plays no role
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-gpt4.api-key", "key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-gpt4.chat-model.model-name", "gpt-4o-mini")
            // OpenAI base URL without trailing slash -> responses
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-no-slash.api-key", "key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-no-slash.base-url", "https://api.openai.com/v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-no-slash.chat-model.model-name", "gpt-6-sol")
            // OpenAI base URL, but explicit mode wins -> chat-completion
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-explicit-completion.api-key", "key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.openai-explicit-completion.chat-model.mode",
                    "chat-completion")
            // custom base URL (OpenAI-compatible endpoint), GPT-6 model -> chat-completion
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.custom-gpt6.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.custom-gpt6.chat-model.model-name", "gpt-6-luna")
            // custom base URL, explicit runtime mode -> responses
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.custom-explicit-responses.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.custom-explicit-responses.chat-model.mode", "responses");

    @Inject
    @ModelName("openai-default")
    ChatModel openAiDefaultModel;

    @Inject
    @ModelName("openai-default")
    StreamingChatModel openAiDefaultStreamingModel;

    @Inject
    @ModelName("openai-gpt4")
    ChatModel openAiGpt4Model;

    @Inject
    @ModelName("openai-no-slash")
    ChatModel openAiNoSlashModel;

    @Inject
    @ModelName("openai-explicit-completion")
    ChatModel openAiExplicitCompletionModel;

    @Inject
    @ModelName("custom-gpt6")
    ChatModel customGpt6Model;

    @Inject
    @ModelName("custom-gpt6")
    StreamingChatModel customGpt6StreamingModel;

    @Inject
    @ModelName("custom-explicit-responses")
    ChatModel customExplicitResponsesModel;

    @Inject
    @ModelName("custom-explicit-responses")
    StreamingChatModel customExplicitResponsesStreamingModel;

    @BeforeEach
    void setup() {
        resetRequests();
        resetMappings();
        stubChatCompletionEndpoint();
        stubResponsesEndpoint();
    }

    @Test
    void openAiBaseUrlWithDefaultModelUsesResponses() {
        assertThat(ClientProxy.unwrap(openAiDefaultModel)).isInstanceOf(OpenAiResponsesChatModel.class);
        assertThat(ClientProxy.unwrap(openAiDefaultStreamingModel)).isInstanceOf(OpenAiResponsesStreamingChatModel.class);
    }

    @Test
    void openAiBaseUrlWithPreGpt6ModelUsesResponses() {
        assertThat(ClientProxy.unwrap(openAiGpt4Model)).isInstanceOf(OpenAiResponsesChatModel.class);
    }

    @Test
    void openAiBaseUrlWithoutTrailingSlashUsesResponses() {
        assertThat(ClientProxy.unwrap(openAiNoSlashModel)).isInstanceOf(OpenAiResponsesChatModel.class);
    }

    @Test
    void explicitModeWinsOverAutoDetection() {
        assertThat(ClientProxy.unwrap(openAiExplicitCompletionModel)).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void customBaseUrlWithGpt6ModelUsesChatCompletion() {
        assertThat(ClientProxy.unwrap(customGpt6Model)).isInstanceOf(OpenAiChatModel.class);
        assertThat(ClientProxy.unwrap(customGpt6StreamingModel)).isInstanceOf(OpenAiStreamingChatModel.class);

        assertThat(customGpt6Model.chat("hi")).isEqualTo("Hello from Chat Completions!");
        wiremock().verifyThat(1, postRequestedFor(urlEqualTo("/v1/chat/completions")));
        wiremock().verifyThat(0, postRequestedFor(urlEqualTo("/v1/responses")));
    }

    @Test
    void explicitRuntimeResponsesModeUsesResponses() {
        assertThat(ClientProxy.unwrap(customExplicitResponsesModel)).isInstanceOf(OpenAiResponsesChatModel.class);
        assertThat(ClientProxy.unwrap(customExplicitResponsesStreamingModel))
                .isInstanceOf(OpenAiResponsesStreamingChatModel.class);

        assertThat(customExplicitResponsesModel.chat("hi")).isEqualTo("Hello from Responses API!");
        wiremock().verifyThat(0, postRequestedFor(urlEqualTo("/v1/chat/completions")));
        wiremock().verifyThat(1, postRequestedFor(urlEqualTo("/v1/responses")));
    }

    private void stubChatCompletionEndpoint() {
        wiremock().register(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "chatcmpl-123",
                                  "object": "chat.completion",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "message": {
                                        "role": "assistant",
                                        "content": "Hello from Chat Completions!"
                                      },
                                      "finish_reason": "stop"
                                    }
                                  ],
                                  "usage": { "prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3 }
                                }
                                """)));
    }

    private void stubResponsesEndpoint() {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "resp_123",
                                  "model": "gpt-6-luna",
                                  "status": "completed",
                                  "output": [
                                    {
                                      "type": "message",
                                      "role": "assistant",
                                      "content": [ { "type": "output_text", "text": "Hello from Responses API!" } ]
                                    }
                                  ],
                                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                }
                                """)));
    }
}
