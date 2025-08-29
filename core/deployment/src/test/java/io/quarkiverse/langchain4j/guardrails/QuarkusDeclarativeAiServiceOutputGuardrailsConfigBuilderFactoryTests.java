package io.quarkiverse.langchain4j.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.config.GuardrailsConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkus.test.QuarkusUnitTest;

class QuarkusDeclarativeAiServiceOutputGuardrailsConfigBuilderFactoryTests {
    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.guardrails.max-retries", "10");

    @Inject
    LangChain4jConfig config;

    @Test
    void hasCorrectConfigSettings() {
        assertThat(this.config)
                .isNotNull()
                .extracting(LangChain4jConfig::guardrails)
                .isNotNull()
                .extracting(GuardrailsConfig::maxRetries)
                .isEqualTo(10);
    }
}
