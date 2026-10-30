package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import io.quarkus.test.QuarkusUnitTest;

public class JinferTranscriptionBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false");

    @Inject
    AudioTranscriptionModel transcriptionModel;

    @Test
    void transcriptionBeanIsProducedAndDisabled() {
        assertThat(transcriptionModel).isNotNull();
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> transcriptionModel.transcribe(null));
    }
}
