package io.quarkiverse.langchain4j.runtime.config;

import io.smallrye.config.WithDefault;

public interface TracingConfig {

    /**
     * If enabled, the prompt is included on the generated spans
     */
    @WithDefault("false")
    Boolean includePrompt();

    /**
     * If enabled, the completion is included on the generated spans
     */
    @WithDefault("false")
    Boolean includeCompletion();
}
