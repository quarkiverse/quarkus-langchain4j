package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.agentic.runtime.AgenticRuntimeConfig;
import io.quarkus.test.QuarkusUnitTest;

public class AgenticRuntimeConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.agent.dev-ui.eager-init", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost");

    @Inject
    AgenticRuntimeConfig config;

    @Test
    void eagerInitConfigPropertyIsReadable() {
        assertThat(config.devUi().eagerInit()).isFalse();
    }

    @Test
    void defaultMaxIterationsIsEmpty() {
        assertThat(config.defaultMaxIterations()).isEmpty();
    }
}
