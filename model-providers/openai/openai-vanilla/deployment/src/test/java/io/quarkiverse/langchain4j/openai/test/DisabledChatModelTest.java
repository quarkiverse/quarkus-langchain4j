package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

public class DisabledChatModelTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses())
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.enabled", "false")
            .assertException(t -> {
                assertThat(t)
                        .isInstanceOf(ConfigurationException.class)
                        .hasMessageContaining("bean was requested, but no langchain4j providers");
            });

    @Inject
    ChatLanguageModel model;

    @Test
    void test() {
        fail("Should not be called");
    }
}
