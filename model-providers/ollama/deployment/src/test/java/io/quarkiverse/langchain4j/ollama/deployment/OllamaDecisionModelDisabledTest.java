package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaDecisionModelDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.decision-model.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .assertException(t -> {
                assertThat(t)
                        .isInstanceOf(ConfigurationException.class)
                        .hasMessageContaining("bean was requested, but no langchain4j providers");
            });

    @Inject
    DecisionModel model;

    @Test
    void test() {
        fail("Should not be called");
    }
}
