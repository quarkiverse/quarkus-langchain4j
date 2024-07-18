package io.quarkiverse.langchain4j.anthropic.deployment;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicDisabledLanguageModelSmokeTest extends AnthropicSmokeTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.enable-integration", "false");

    @Inject
    ChatLanguageModel chatModel;

    @Inject
    StreamingChatLanguageModel streamingChatModel;

    @Test
    void blocking() {
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> chatModel.generate("Hello, how are you today?"))
                .withMessage("ChatLanguageModel is disabled");
    }

    @Test
    void streaming() {
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> streamingChatModel.generate("Hello, how are you today?", null))
                .withMessage("StreamingChatLanguageModel is disabled");
    }
}
