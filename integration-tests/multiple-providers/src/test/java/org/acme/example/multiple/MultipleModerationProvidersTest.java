package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleModerationProvidersTest {

    @Inject
    ModerationModel defaultModel;

    @Inject
    @ModelName("c1")
    ModerationModel firstNamedModel;

    @Test
    void defaultModel() {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(OpenAiModerationModel.class);
    }

    @Test
    void firstNamedModel() {
        assertThat(ClientProxy.unwrap(firstNamedModel)).isInstanceOf(OpenAiModerationModel.class);
    }
}
