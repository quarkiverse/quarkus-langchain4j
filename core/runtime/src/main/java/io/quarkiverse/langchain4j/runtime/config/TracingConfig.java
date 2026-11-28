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

    /**
     * If enabled, the arguments of an AI service method are included on its span, as a JSON object keyed by parameter
     * name in the {@code langchain4j.aiservice.arguments} attribute.
     * <p>
     * Parameters the framework consumes itself are left out: the memory id, {@code InvocationParameters},
     * {@code ChatRequestParameters}, {@code @ModelName}, {@code @UserName}, and image, audio, PDF, video and
     * {@code Content} parameters.
     */
    @WithDefault("false")
    Boolean includeAiServiceArguments();

    /**
     * If enabled, the result of an AI service method is included on its span, in the {@code langchain4j.aiservice.result}
     * attribute.
     * <p>
     * A {@code String} is recorded as-is and any other value as JSON. A {@code Result} is recorded as its content. For a
     * streaming method, the streamed text is recorded once the stream completes. Nothing is recorded for image results.
     */
    @WithDefault("false")
    Boolean includeAiServiceResult();
}
