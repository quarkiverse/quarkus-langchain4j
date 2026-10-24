package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class NamedModelModeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // "completion" model is pinned to the chat-completion mode
            .overrideConfigKey("quarkus.langchain4j.openai.completion.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.completion.api-key", "key-completion")
            // "responses" model uses the responses mode
            .overrideConfigKey("quarkus.langchain4j.openai.responses.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.responses.api-key", "key-responses");

    @Inject
    @ModelName("completion")
    ChatModel completionModel;

    @Inject
    @ModelName("responses")
    ChatModel responsesModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
    }

    @Test
    void completionModelTargetsChatCompletionsEndpoint() {
        mock.chat().reply("Hello from Chat Completions!");

        String result = completionModel.chat("hi");
        assertThat(result).isEqualTo("Hello from Chat Completions!");

        // the only request went to the chat completions endpoint, none to the responses endpoint
        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer key-completion");
    }

    @Test
    void responsesModelTargetsResponsesEndpoint() {
        mock.chat().reply("Hello from Responses API!");

        String result = responsesModel.chat("hi");
        assertThat(result).isEqualTo("Hello from Responses API!");

        // the only request went to the responses endpoint, none to the chat completions endpoint
        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer key-responses");
    }
}
