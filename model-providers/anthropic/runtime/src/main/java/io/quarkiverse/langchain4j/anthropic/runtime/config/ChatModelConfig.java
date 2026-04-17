package io.quarkiverse.langchain4j.anthropic.runtime.config;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {
    /**
     * Model name to use
     */
    @WithDefault("claude-3-haiku-20240307")
    String modelName();

    /**
     * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will make the output more random, while
     * lower values like 0.2 will make it more focused and deterministic.
     * <p>
     * It is generally recommended to set this or the {@code top-k} property but not both.
     */
    @ConfigDocDefault("0.7")
    @WithDefault("${quarkus.langchain4j.temperature}")
    OptionalDouble temperature();

    /**
     * The maximum number of tokens to generate in the completion.
     * <p>
     * The token count of your prompt plus {@code max_tokens} cannot exceed the model's context length
     */
    @WithDefault("1024")
    Integer maxTokens();

    /**
     * Double (0.0-1.0). Nucleus sampling, where the model considers the results of the tokens with top_p probability mass.
     * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * <p>
     * It is generally recommended to set this or the {@code temperature} property but not both.
     */
    @ConfigDocDefault("1.0")
    OptionalDouble topP();

    /**
     * Reduces the probability of generating nonsense. A higher value (e.g. 100) will give more diverse answers, while a lower
     * value (e.g. 10) will be more conservative
     */
    @ConfigDocDefault("40")
    OptionalInt topK();

    /**
     * The maximum number of times to retry. 1 means exactly one attempt, with retrying disabled.
     *
     * @deprecated Using the fault tolerance mechanisms built in Langchain4j is not recommended. If possible,
     *             use MicroProfile Fault Tolerance instead.
     */
    @WithDefault("1")
    Integer maxRetries();

    /**
     * The custom text sequences that will cause the model to stop generating
     */
    Optional<List<String>> stopSequences();

    /**
     * Whether chat model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Cache system messages to reduce costs for repeated prompts.
     * Requires minimum 1024 tokens (Claude Opus/Sonnet) or 2048-4096 tokens (Haiku).
     * Supported models: Claude Opus 4.1, Sonnet 4.5, Haiku 4.5, and later models.
     */
    @WithDefault("false")
    Boolean cacheSystemMessages();

    /**
     * Cache tool definitions to reduce costs.
     * Requires minimum 1024 tokens (Claude Opus/Sonnet) or 2048-4096 tokens (Haiku).
     * Supported models: Claude Opus 4.1, Sonnet 4.5, Haiku 4.5, and later models.
     */
    @WithDefault("false")
    Boolean cacheTools();

    /**
     * Specifies the desired format for the model's output.
     * <p>
     * <strong>Allowable values:</strong> <code>[text, json]</code>
     */
    Optional<String> responseFormat();

    /**
     * Thinking related configuration
     */
    ThinkingConfig thinking();

    /**
     * Tool Search Tool, which allows Claude to use search tools to access thousands of tools without consuming its context
     * window
     */
    ToolSearchConfig toolSearch();

    /**
     * Programmatic tool calling configuration, which allows Claude to invoke tools in a code execution environment reducing the
     * impact on the model’s context window
     */
    ProgrammaticToolCallingConfig programmaticToolCalling();

    /**
     * Tool Use Examples, which provides a universal standard for demonstrating how to effectively use a given tool
     */
    ToolUseExamplesConfig toolUseExamples();

    @ConfigGroup
    interface ThinkingConfig {

        /**
         * The thinking type to enable Claude's reasoning process
         */
        Optional<String> type();

        /**
         * The token budget for the model's thinking process
         */
        Optional<Integer> budgetTokens();

        /**
         * Whether thinking results should be returned in the response
         */
        @ConfigDocDefault("false")
        Optional<Boolean> returnThinking();

        /**
         * Whether previously stored thinking should be sent in follow-up requests
         */
        @ConfigDocDefault("true")
        Optional<Boolean> sendThinking();

        /**
         * Controls how thinking content is returned by the Anthropic API.
         * <p>
         * Valid values: {@code "summarized"} and {@code "omitted"}. On Claude Opus 4.7
         * the server default is {@code "omitted"} (thinking blocks are empty in the
         * response). On earlier Opus/Sonnet models the default is {@code "summarized"}.
         * Set to {@code "summarized"} explicitly on Opus 4.7+ if your product renders
         * thinking text to users.
         */
        Optional<String> display();

        /**
         * Enable interleaved thinking for Claude 4 models, allowing reasoning between tool calls.
         * Requires Claude 4 model (e.g., claude-opus-4-20250514) and thinking.type: enabled.
         */
        @WithDefault("false")
        Optional<Boolean> interleaved();
    }

    @ConfigGroup
    interface ToolSearchConfig {

        /**
         * Enable Anthropic's Tool Search Tool for on-demand tool discovery.
         * When enabled, this automatically adds the tool search server tool, sets the
         * required beta header, and enables the "defer_loading" tool metadata key.
         * Tools annotated with {@code @Tool(metadata = "{\"defer_loading\": true}")}
         * will be discovered on demand instead of loaded upfront.
         */
        @WithDefault("false")
        Boolean enabled();

        /**
         * The type of tool search to use.
         * Available types: "regex" (default) or "bm25".
         */
        @WithDefault("regex")
        String type();
    }

    @ConfigGroup
    interface ProgrammaticToolCallingConfig {
        /**
         * Enable Anthropic's Programmatic Tool Calling via the Code Execution server tool.
         * When enabled, this automatically adds the code execution server tool, the {@code "allowed_callers"}
         * key is sent with tool definitions, and the required beta header is set.
         * Claude can orchestrate multiple tool calls from within generated Python code,
         * keeping intermediate results out of the context window rather than accumulating
         * them in the conversation, significantly reducing token consumption.
         * <p>
         * Tools that should be callable from code must include:
         * {@code @Tool(metadata = "{\"allowed_callers\": [\"code_execution_20250825\"]}")}
         */
        @WithDefault("false")
        Boolean enabled();
    }

    @ConfigGroup
    interface ToolUseExamplesConfig {
        /**
         * Enable Anthropic's Tool Use Examples feature.
         * When enabled, the {@code "input_examples"} key is sent with tool definitions,
         * and the required beta header is set. Providing concrete input examples alongside
         * tool schemas helps Claude learn correct parameter usage, formats, and conventions
         * that cannot be expressed in JSON Schema alone.
         * <p>
         * Tools with examples must include:
         * {@code @Tool(metadata = "{\"input_examples\": [{...}, ...]}")}
         */
        @WithDefault("false")
        Boolean enabled();
    }
}
