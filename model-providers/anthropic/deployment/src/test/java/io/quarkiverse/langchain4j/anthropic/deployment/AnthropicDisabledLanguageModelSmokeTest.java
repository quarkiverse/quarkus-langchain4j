package io.quarkiverse.langchain4j.anthropic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicDisabledLanguageModelSmokeTest extends AnthropicSmokeTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.enable-integration", "false");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void blocking() {
        assertThat(ClientProxy.unwrap(chatModel))
                .isInstanceOf(DisabledChatModel.class);

        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> chatModel.chat("Hello, how are you today?"))
                .withMessage("ChatModel is disabled");
    }

    @Test
    void streaming() {
        assertThat(ClientProxy.unwrap(streamingChatModel))
                .isInstanceOf(DisabledStreamingChatModel.class);

        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> streamingChatModel.chat("Hello, how are you today?", null))
                .withMessage("StreamingChatModel is disabled");
    }
}
