package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.guardrail.config.OutputGuardrailsConfig.OutputGuardrailsConfigBuilder;
import dev.langchain4j.spi.guardrail.config.OutputGuardrailsConfigBuilderFactory;
import io.quarkiverse.langchain4j.guardrails.QuarkusOutputGuardrailsConfig.QuarkusOutputGuardrailsConfigBuilder;

public class QuarkusOutputGuardrailsConfigBuilderFactory implements OutputGuardrailsConfigBuilderFactory {
    @Override
    public OutputGuardrailsConfigBuilder get() {
        return new QuarkusOutputGuardrailsConfigBuilder();
    }
}
