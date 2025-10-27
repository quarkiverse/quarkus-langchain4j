package io.quarkiverse.langchain4j.openai.runtime.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

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
    Double temperature();

    /**
     * An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens
     * with topP probability mass.
     * 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * It is recommended to alter this or temperature, but not both.
     */
    @WithDefault("1.0")
    Double topP();

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
}
