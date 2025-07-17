package io.quarkiverse.langchain4j.guardrails;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import io.quarkiverse.langchain4j.runtime.config.GuardrailsConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.smallrye.config.SmallRyeConfig;

public class QuarkusOutputGuardrailsConfig implements OutputGuardrailsConfig {
    private final GuardrailsConfig guardrailsConfig;

    public QuarkusOutputGuardrailsConfig() {
        this.guardrailsConfig = ConfigProvider.getConfig()
                .unwrap(SmallRyeConfig.class)
                .getConfigMapping(LangChain4jConfig.class)
                .guardrails();
    }

    @Override
    public int maxRetries() {
        return this.guardrailsConfig.maxRetries();
    }
}
