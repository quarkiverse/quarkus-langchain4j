package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig.OutputGuardrailsConfigBuilder;
import dev.langchain4j.spi.guardrail.config.OutputGuardrailsConfigBuilderFactory;

public class QuarkusOutputGuardrailsConfigBuilderFactory implements OutputGuardrailsConfigBuilderFactory {
    @Override
    public OutputGuardrailsConfigBuilder get() {
        return new QuarkusOutputGuardrailsConfigBuilder();
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
