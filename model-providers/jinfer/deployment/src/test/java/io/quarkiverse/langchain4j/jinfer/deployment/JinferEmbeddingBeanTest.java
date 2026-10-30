package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class JinferEmbeddingBeanTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false");

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void embeddingBeanIsProducedAndDisabled() {
        assertThat(ClientProxy.unwrap(embeddingModel)).isInstanceOf(DisabledEmbeddingModel.class);
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(() -> embeddingModel.embed("Hello"));
    }
}
