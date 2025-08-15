package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig.InputGuardrailsConfigBuilder;
import dev.langchain4j.spi.guardrail.config.InputGuardrailsConfigBuilderFactory;

public class QuarkusInputGuardrailsConfigBuilderFactory implements InputGuardrailsConfigBuilderFactory {
    @Override
    public InputGuardrailsConfigBuilder get() {
        return new QuarkusInputGuardrailsConfigBuilder();
    }

    private static class QuarkusInputGuardrailsConfigBuilder implements InputGuardrailsConfigBuilder {
        @Override
        public InputGuardrailsConfig build() {
            return new QuarkusInputGuardrailsConfig();
        }
    }
}
