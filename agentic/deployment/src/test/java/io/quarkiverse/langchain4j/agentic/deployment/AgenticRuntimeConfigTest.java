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
            .overrideRuntimeConfigKey("quarkus.langchain4j.agent.max-iterations", "15")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agent.\"story-loop\".max-iterations", "25")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agent.\"remote-writer\".a2a-server-url",
                    "https://prod.example.com")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost");

    @Inject
    AgenticRuntimeConfig config;

    @Test
    void eagerInitConfigPropertyIsReadable() {
        assertThat(config.devUi().eagerInit()).isFalse();
    }

    @Test
    void defaultMaxIterationsIsReadable() {
        assertThat(config.defaultConfig().maxIterations()).hasValue(15);
    }

    @Test
    void namedAgentMaxIterationsOverridesDefault() {
        var storyLoop = config.namedConfig().get("story-loop");
        assertThat(storyLoop).isNotNull();
        assertThat(storyLoop.maxIterations()).hasValue(25);
    }

    @Test
    void namedAgentA2AServerUrlIsReadable() {
        var remoteWriter = config.namedConfig().get("remote-writer");
        assertThat(remoteWriter).isNotNull();
        assertThat(remoteWriter.a2aServerUrl()).hasValue("https://prod.example.com");
    }

    @Test
    void unnamedAgentInheritsDefaults() {
        var storyLoop = config.namedConfig().get("story-loop");
        assertThat(storyLoop.a2aServerUrl()).isEmpty();
    }

    @Test
    void maxAgentsInvocationsDefaultsToEmpty() {
        assertThat(config.defaultConfig().maxAgentsInvocations()).isEmpty();
    }
}
