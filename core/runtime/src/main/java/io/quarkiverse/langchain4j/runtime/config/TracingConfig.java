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

    /**
     * If enabled, tool call arguments are included on the generated spans
     */
    @WithDefault("false")
    Boolean includeToolArguments();

    /**
     * If enabled, tool call results are included on the generated spans
     */
    @WithDefault("false")
    Boolean includeToolResult();
}
