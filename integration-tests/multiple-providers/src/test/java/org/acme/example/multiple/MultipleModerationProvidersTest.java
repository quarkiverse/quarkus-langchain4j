package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
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

    @Inject
    @Any
    Instance<ModerationModel> anyModel;

    @Test
    void defaultModel() {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(OpenAiModerationModel.class);
    }

    @Test
    void firstNamedModel() {
        assertThat(ClientProxy.unwrap(firstNamedModel)).isInstanceOf(OpenAiModerationModel.class);
    }

    @Test
    void anyInstanceInjection() {
        // c11 model has no direct injection point; we only request it via Instance<T>
        assertThat(anyModel.handlesStream()
                .anyMatch(handle -> handle.getBean().getQualifiers().contains(ModelName.Literal.of("c11")))).isTrue();
    }
}
