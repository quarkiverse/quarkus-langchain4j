package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.audio.TextToSpeechModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferTtsBeanTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.custom.enable-integration", "false");

    @Inject
    TextToSpeechModel ttsModel;

    @Inject
    @ModelName("custom")
    TextToSpeechModel customTtsModel;

    @Test
    void defaultTtsBeanIsProducedAndDisabled() {
        assertThat(ttsModel).isNotNull();
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> ttsModel.synthesize("Hello"));
    }

    @Test
    void namedTtsBeanIsProduced() {
        assertThat(customTtsModel).isNotNull();
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> customTtsModel.synthesize("Hello"));
    }
}
