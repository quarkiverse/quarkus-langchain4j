package io.quarkiverse.langchain4j.guardrails;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import io.quarkiverse.langchain4j.runtime.config.GuardrailsConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.smallrye.config.SmallRyeConfig;

public class QuarkusOutputGuardrailsConfig implements OutputGuardrailsConfig {
    private final int maxRetries;
    private final GuardrailsConfig guardrailsConfig;

    public QuarkusOutputGuardrailsConfig(QuarkusOutputGuardrailsConfigBuilder builder) {
        this.maxRetries = builder.maxRetries;
        this.guardrailsConfig = ConfigProvider.getConfig()
                .unwrap(SmallRyeConfig.class)
                .getConfigMapping(LangChain4jConfig.class)
                .guardrails();
    }

    @Override
    public int maxRetries() {
        return (this.guardrailsConfig.maxRetries() == GuardrailsConfig.MAX_RETRIES_DEFAULT) ? this.maxRetries
                : this.guardrailsConfig.maxRetries();
    }

    static class QuarkusOutputGuardrailsConfigBuilder implements OutputGuardrailsConfigBuilder {
        private int maxRetries = GuardrailsConfig.MAX_RETRIES_DEFAULT;

        @Override
        public OutputGuardrailsConfigBuilder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        @Override
        public OutputGuardrailsConfig build() {
            return new QuarkusOutputGuardrailsConfig(this);
        }
    }
}
