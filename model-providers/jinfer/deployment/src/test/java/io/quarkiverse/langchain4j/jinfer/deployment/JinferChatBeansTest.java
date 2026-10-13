package io.quarkiverse.langchain4j.jinfer.deployment;

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

public class JinferChatBeansTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void chatBeanIsProducedAndDisabled() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(DisabledChatModel.class);
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> chatModel.chat("Hello"));
    }

    @Test
    void streamingChatBeanIsProducedAndDisabled() {
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(DisabledStreamingChatModel.class);
    }
}
