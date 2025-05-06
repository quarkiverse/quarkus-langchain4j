package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.assertj.core.api.ThrowableAssert;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkus.test.QuarkusUnitTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockChatModelDisabledTest extends BedrockTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.enable-integration", "false");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void should_disable_chat_model() {
        // given

        // when
        ThrowableAssert.ThrowingCallable callable = () -> chatModel.chat("Hello, how are you today?");

        // then
        assertThatThrownBy(callable).isInstanceOf(ModelDisabledException.class);
    }

    @Test
    void should_disable_streaming_chat_model() {
        // given

        // when
        ThrowableAssert.ThrowingCallable callable = () -> streamingChatModel.chat("Hello, how are you today?", null);

        // then
        assertThatThrownBy(callable).isInstanceOf(ModelDisabledException.class);
    }
}
