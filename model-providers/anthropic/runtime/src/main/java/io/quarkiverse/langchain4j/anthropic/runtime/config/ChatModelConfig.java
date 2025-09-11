package io.quarkiverse.langchain4j.anthropic.runtime.config;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

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
    @WithDefault("40")
    Integer topK();

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
     * The thinking type to enable Claude's reasoning process
     */
    Optional<String> thinkingType();

    /**
     * The token budget for the model's thinking process
     */
    Optional<Integer> thinkingBudgetTokens();

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
}
