package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig.OutputGuardrailsConfigBuilder;
import dev.langchain4j.spi.guardrail.config.OutputGuardrailsConfigBuilderFactory;

public class QuarkusOutputGuardrailsConfigBuilderFactory implements OutputGuardrailsConfigBuilderFactory {
    private static final OutputGuardrailsConfigBuilder INSTANCE = new QuarkusOutputGuardrailsConfigBuilder();

    @Override
    public OutputGuardrailsConfigBuilder get() {
        return INSTANCE;
    }

    private static class QuarkusOutputGuardrailsConfigBuilder implements OutputGuardrailsConfigBuilder {
        @Override
        public OutputGuardrailsConfigBuilder maxRetries(int maxRetries) {
            return this;
        }

        @Override
        public OutputGuardrailsConfig build() {
            return new QuarkusOutputGuardrailsConfig();
        }
    }
}
