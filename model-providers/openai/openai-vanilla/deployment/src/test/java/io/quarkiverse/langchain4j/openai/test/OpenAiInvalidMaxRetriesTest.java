package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.QuarkusUnitTest;

public class OpenAiInvalidMaxRetriesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.api-key", "somekey")
            .overrideConfigKey("quarkus.langchain4j.openai.max-retries", "0")
            .assertException(t -> {
                assertThat(t)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("max-retries must be greater than zero");
            });

    @Inject
    ChatModel model;

    @Test
    void test() {
        fail("Should not be called");
    }
}
