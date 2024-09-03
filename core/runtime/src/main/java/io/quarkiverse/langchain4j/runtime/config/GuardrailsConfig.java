package io.quarkiverse.langchain4j.runtime.config;

import io.smallrye.config.WithDefault;

public interface GuardrailsConfig {

    int MAX_RETRIES_DEFAULT = 3;

    /**
     * Configures the maximum number of retries for the guardrail.
     * Sets it to 0 to disable retries.
     */
    @WithDefault("" + MAX_RETRIES_DEFAULT)
    int maxRetries();

}
