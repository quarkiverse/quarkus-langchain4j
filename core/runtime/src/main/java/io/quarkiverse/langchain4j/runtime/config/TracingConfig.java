package io.quarkiverse.langchain4j.runtime.config;

import io.smallrye.config.WithDefault;

public interface TracingConfig {

    /**
     * If enabled, the prompt is included on the generated model spans, as the {@code gen_ai.system_instructions} and
     * {@code gen_ai.input.messages} attributes of the GenAI semantic conventions.
     * <p>
     * The deprecated {@code gen_ai.prompt} attribute is also recorded for now. It will be removed in a future release.
     */
    @WithDefault("false")
    Boolean includePrompt();

    /**
     * If enabled, the completion is included on the generated model spans, as the {@code gen_ai.output.messages}
     * attribute of the GenAI semantic conventions.
     * <p>
     * The deprecated {@code gen_ai.completion} attribute is also recorded for now. It will be removed in a future release.
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
