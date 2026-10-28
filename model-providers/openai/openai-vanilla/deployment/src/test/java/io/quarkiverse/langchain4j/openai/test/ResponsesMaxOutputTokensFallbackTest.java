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

/**
 * When {@code responses.max-output-tokens} is not set, the chat-completions limits keep applying on the Responses API:
 * {@code max-completion-tokens} first, then the deprecated {@code max-tokens}.
 */
public class ResponsesMaxOutputTokensFallbackTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-completion.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-completion.chat-model.max-completion-tokens", "50")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-completion.chat-model.max-tokens", "40")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-max-tokens.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.from-max-tokens.chat-model.max-tokens", "40")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.explicit.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.explicit.chat-model.responses.max-output-tokens", "100")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.explicit.chat-model.max-completion-tokens", "50");

    @Inject
    @ModelName("from-completion")
    ChatModel fromCompletionModel;

    @Inject
    @ModelName("from-max-tokens")
    ChatModel fromMaxTokensModel;

    @Inject
    @ModelName("explicit")
    ChatModel explicitModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        mock.chat().reply("hello");
    }

    @Test
    void maxCompletionTokensWinsOverMaxTokens() {
        fromCompletionModel.chat("hi");
        assertThat(sentMaxOutputTokens()).isEqualTo(50);
    }

    @Test
    void maxTokensIsUsedAsLastResort() {
        fromMaxTokensModel.chat("hi");
        assertThat(sentMaxOutputTokens()).isEqualTo(40);
    }

    @Test
    void explicitMaxOutputTokensWins() {
        explicitModel.chat("hi");
        assertThat(sentMaxOutputTokens()).isEqualTo(100);
    }

    private int sentMaxOutputTokens() {
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        return request.bodyAsJson().path("max_output_tokens").intValue();
    }
}
