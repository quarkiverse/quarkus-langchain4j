package io.quarkiverse.langchain4j.openai.runtime.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Which OpenAI chat API to use.
     * <p>
     * {@code chat-completions} uses the legacy {@code POST /v1/chat/completions} endpoint.
     * {@code responses} uses the newer {@code POST /v1/responses} endpoint, which is required for some
     * newer models and features (reasoning summaries, server tools, response chaining via
     * {@code previous-response-id}, etc.).
     */
    @WithDefault("chat-completions")
    Api api();

    /**
     * Model name to use
     */
    @WithDefault("gpt-4o-mini")
    String modelName();

    /**
     * What sampling temperature to use, with values between 0 and 2.
     * Higher values means the model will take more risks.
     * A value of 0.9 is good for more creative applications, while 0 (argmax sampling) is good for ones with a well-defined
     * answer.
     * It is recommended to alter this or topP, but not both.
     */
    @WithDefault("${quarkus.langchain4j.temperature:1.0}")
    Optional<Double> temperature();

    /**
     * An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens
     * with topP probability mass.
     * 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * It is recommended to alter this or temperature, but not both.
     */
    @WithDefault("1.0")
    Optional<Double> topP();

    /**
     * The maximum number of tokens to generate in the completion. The token count of your prompt plus max_tokens can't exceed
     * the model's context length.
     * Most models have a context length of 2048 tokens (except for the newest models, which support 4096).
     *
     * @deprecated For newer OpenAI models, use {@code maxCompletionTokens} instead
     */
    @Deprecated
    Optional<Integer> maxTokens();

    /**
     * An upper bound for the number of tokens that can be generated for a completion, including visible output tokens and
     * reasoning tokens.
     */
    Optional<Integer> maxCompletionTokens();

    /**
     * An upper bound for the number of tokens that can be generated for a response, including visible output tokens and
     * reasoning tokens.
     * <p>
     * Used when {@link #api()} is set to {@code responses}. If not set, {@link #maxCompletionTokens()} is used as a fallback.
     */
    Optional<Integer> maxOutputTokens();

    /**
     * Number between -2.0 and 2.0.
     * Positive values penalize new tokens based on whether they appear in the text so far, increasing the model's likelihood to
     * talk about new topics.
     */
    @WithDefault("0")
    Double presencePenalty();

    /**
     * Number between -2.0 and 2.0.
     * Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's
     * likelihood to repeat the same line verbatim.
     */
    @WithDefault("0")
    Double frequencyPenalty();

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
     * The response format the model should use.
     * Some models are not compatible with some response formats, make sure to review OpenAI documentation.
     */
    Optional<String> responseFormat();

    /**
     * Whether responses follow JSON Schema for Structured Outputs
     */
    Optional<Boolean> strictJsonSchema();

    /**
     * The list of stop words to use.
     *
     * @return
     */
    Optional<List<String>> stop();

    /**
     * Constrains effort on reasoning for reasoning models.
     * Currently supported values are {@code minimal}, {@code low}, {@code medium}, and {@code high}.
     * Reducing reasoning effort can result in faster responses and fewer tokens used on reasoning in a response.
     * <p>
     * Note: The {@code gpt-5-pro} model defaults to (and only supports) high reasoning effort.
     */
    Optional<String> reasoningEffort();

    /**
     * Specifies the processing type used for serving the request.
     * <p>
     * If set to {@code auto}, then the request will be processed with the service tier configured in the Project settings.
     * If set to {@code default}, then the request will be processed with the standard pricing and performance for the selected
     * model.
     * If set to {@code flex} or {@code priority}, then the request will be processed with the corresponding service tier.
     * When not set, the default behavior is {@code auto}.
     * <p>
     * When the service tier parameter is set, the response body will include the {@code service_tier} value based on the
     * processing mode actually used to serve the request.
     * This response value may be different from the value set in the parameter.
     */
    @ConfigDocDefault("default")
    Optional<String> serviceTier();

    /**
     * Whether to store the response for later retrieval via the Responses API.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<Boolean> store();

    /**
     * The ID of a previous response to continue a multi-turn conversation.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<String> previousResponseId();

    /**
     * Truncation strategy for model responses.
     * Supported values are {@code auto} and {@code disabled}.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<String> truncation();

    /**
     * Additional output data to include in the model response.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<List<String>> include();

    /**
     * Level of detail for reasoning summaries returned by reasoning models.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<String> reasoningSummary();

    /**
     * Controls the verbosity of the model's text output.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<String> textVerbosity();

    /**
     * A key used to identify prompts for caching purposes.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<String> promptCacheKey();

    /**
     * Controls how long prompt cache entries are retained.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<String> promptCacheRetention();

    /**
     * The number of most likely tokens to return at each position, each with an associated log probability.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<Integer> topLogprobs();

    /**
     * Whether to allow parallel tool calls when tools are enabled.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<Boolean> parallelToolCalls();

    /**
     * Maximum number of tool calls the model may make in a single response.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<Integer> maxToolCalls();

    /**
     * A stable identifier used to help detect users that may be violating OpenAI's usage policies.
     * Only used when {@link #api()} is set to {@code responses}.
     */
    Optional<String> safetyIdentifier();

    /**
     * Whether to include obfuscation fields in streaming responses.
     * Only used when {@link #api()} is set to {@code responses} with a streaming chat model.
     */
    Optional<Boolean> streamIncludeObfuscation();

    enum Api {
        CHAT_COMPLETIONS,
        RESPONSES
    }
}
