package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.bam.BamModerationModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleModerationProvidersTest {

    @Inject
    ModerationModel defaultModel;

    @Inject
    @ModelName("c1")
    ModerationModel firstNamedModel;

    @Inject
    @ModelName("c4")
    ModerationModel fourthNamedModel;

    @Test
    void defaultModel() {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(OpenAiModerationModel.class);
    }

    @Test
    void firstNamedModel() {
        assertThat(ClientProxy.unwrap(firstNamedModel)).isInstanceOf(OpenAiModerationModel.class);
    }

    @Test
    void fourthNamedModel() {
        assertThat(ClientProxy.unwrap(fourthNamedModel)).isInstanceOf(BamModerationModel.class);
    }
}
