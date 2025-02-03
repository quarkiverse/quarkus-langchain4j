package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.Moderate;
import io.quarkus.test.QuarkusUnitTest;

public class NoModerationProvidedTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    public void test() {
        assertThatThrownBy(() -> AiServices.create(Assistant.class, OpenAiChatModel.builder().apiKey("whatever").build()))
                .isInstanceOf(IllegalConfigurationException.class);
    }

    interface Assistant {

        @Moderate
        String chat(String message);
    }
}
