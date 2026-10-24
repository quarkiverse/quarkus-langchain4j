package io.quarkiverse.langchain4j.openai.test;

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
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies how {@code quarkus.langchain4j.openai.chat-model.mode} is resolved when it is not set explicitly:
 * the Responses API is selected when talking to OpenAI itself, any other base URL keeps using Chat Completions.
 * An explicit {@code mode} always wins and is a runtime property.
 * <p>
 * Against the mock every model talks to the mock's URL, so the mock pins the mode a model would select in production
 * from the build-time configuration: a model with no base URL of its own, or with OpenAI's, targets the Responses API,
 * while a model with another base URL stays on Chat Completions, as it would against the compatible endpoint it was
 * configured for. The keys are therefore set at build time, where the mock reads them.
 */
public class ChatModelAutoModeTest {

    // an OpenAI-compatible endpoint of the application's own; the mock redirects the model to itself
    private static final String COMPATIBLE_ENDPOINT = "http://localhost:11434/v1";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // OpenAI base URL, default model -> responses
            .overrideConfigKey("quarkus.langchain4j.openai.openai-default.api-key", "key")
            // OpenAI base URL, pre-GPT-6 model -> still responses, the model name plays no role
            .overrideConfigKey("quarkus.langchain4j.openai.openai-gpt4.api-key", "key")
            .overrideConfigKey("quarkus.langchain4j.openai.openai-gpt4.chat-model.model-name", "gpt-4o-mini")
            // OpenAI base URL without trailing slash -> responses
            .overrideConfigKey("quarkus.langchain4j.openai.openai-no-slash.api-key", "key")
            .overrideConfigKey("quarkus.langchain4j.openai.openai-no-slash.base-url", "https://api.openai.com/v1")
            .overrideConfigKey("quarkus.langchain4j.openai.openai-no-slash.chat-model.model-name", "gpt-6-sol")
            // OpenAI base URL, but explicit mode wins -> chat-completion
            .overrideConfigKey("quarkus.langchain4j.openai.openai-explicit-completion.api-key", "key")
            .overrideConfigKey("quarkus.langchain4j.openai.openai-explicit-completion.chat-model.mode", "chat-completion")
            // custom base URL (OpenAI-compatible endpoint), GPT-6 model -> chat-completion
            .overrideConfigKey("quarkus.langchain4j.openai.custom-gpt6.base-url", COMPATIBLE_ENDPOINT)
            .overrideConfigKey("quarkus.langchain4j.openai.custom-gpt6.chat-model.model-name", "gpt-6-luna")
            // custom base URL, explicit mode -> responses
            .overrideConfigKey("quarkus.langchain4j.openai.custom-explicit-responses.base-url", COMPATIBLE_ENDPOINT)
            .overrideConfigKey("quarkus.langchain4j.openai.custom-explicit-responses.chat-model.mode", "responses");

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

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
    }

    @Test
    void openAiBaseUrlWithDefaultModelUsesResponses() {
        assertThat(ClientProxy.unwrap(openAiDefaultModel)).isInstanceOf(OpenAiResponsesChatModel.class);
        assertThat(ClientProxy.unwrap(openAiDefaultStreamingModel)).isInstanceOf(OpenAiResponsesStreamingChatModel.class);

        mock.chat().reply("Hello from Responses API!");
        assertThat(openAiDefaultModel.chat("hi")).isEqualTo("Hello from Responses API!");
        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo("/v1/responses"));
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

        mock.chat().reply("Hello from Chat Completions!");
        assertThat(customGpt6Model.chat("hi")).isEqualTo("Hello from Chat Completions!");
        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo("/v1/chat/completions"));
    }

    @Test
    void explicitRuntimeResponsesModeUsesResponses() {
        assertThat(ClientProxy.unwrap(customExplicitResponsesModel)).isInstanceOf(OpenAiResponsesChatModel.class);
        assertThat(ClientProxy.unwrap(customExplicitResponsesStreamingModel))
                .isInstanceOf(OpenAiResponsesStreamingChatModel.class);

        mock.chat().reply("Hello from Responses API!");
        assertThat(customExplicitResponsesModel.chat("hi")).isEqualTo("Hello from Responses API!");
        assertThat(mock.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo("/v1/responses"));
    }
}
