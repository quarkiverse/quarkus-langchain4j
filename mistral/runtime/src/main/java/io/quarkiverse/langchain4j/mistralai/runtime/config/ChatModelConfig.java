package io.quarkiverse.langchain4j.mistralai.runtime.config;

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
    @WithDefault("mistral-tiny")
    String modelName();

    /**
     * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will make the output more random, while
     * lower values like 0.2 will make it more focused and deterministic.
     * <p>
     * It is generally recommended to set this or the {@code top-k} property but not both.
     */
    @ConfigDocDefault("0.7")
    OptionalDouble temperature();

    /**
     * The maximum number of tokens to generate in the completion.
     * <p>
     * The token count of your prompt plus {@code max_tokens} cannot exceed the model's context length
     */
    OptionalInt maxTokens();

    /**
     * Double (0.0-1.0). Nucleus sampling, where the model considers the results of the tokens with top_p probability mass.
     * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * <p>
     * It is generally recommended to set this or the {@code temperature} property but not both.
     */
    @ConfigDocDefault("1.0")
    OptionalDouble topP();

    /**
     * Whether to inject a safety prompt before all conversations
     */
    Optional<Boolean> safePrompt();

    /**
     * The seed to use for random sampling. If set, different calls will generate deterministic results.
     */
    OptionalInt randomSeed();

    /**
     * Whether chat model requests should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.mistralai.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.mistralai.log-responses}")
    Optional<Boolean> logResponses();

}
