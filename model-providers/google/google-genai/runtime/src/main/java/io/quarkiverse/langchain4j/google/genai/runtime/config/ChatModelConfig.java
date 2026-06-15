package io.quarkiverse.langchain4j.google.genai.runtime.config;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * The id of the model to use.
     *
     * @see <a href=
     *      "https://ai.google.dev/gemini-api/docs/models/gemini">https://ai.google.dev/gemini-api/docs/models/gemini</a>
     */
    @WithDefault("gemini-2.5-flash")
    String modelId();

    /**
     * The temperature is used for sampling during response generation, which occurs when topP and topK are applied.
     * Temperature controls the degree of randomness in token selection. Lower temperatures are good for prompts that require a
     * less open-ended or creative response, while higher temperatures can lead to more diverse or creative results.
     * <p>
     * Range: 0.0 - 2.0
     */
    @WithDefault("${quarkus.langchain4j.temperature:1.0}")
    Optional<Double> temperature();

    /**
     * Maximum number of tokens that can be generated in the response. The maximum value depends on the model being used.
     */
    @WithDefault("8192")
    Integer maxOutputTokens();

    /**
     * Top-P changes how the model selects tokens for output. Tokens are selected from the most (see top-K) to least probable
     * until the sum of their probabilities equals the top-P value.
     * <p>
     * Range: 0.0 - 1.0
     * <p>
     * Default: 0.95
     */
    OptionalDouble topP();

    /**
     * Top-K changes how the model selects tokens for output. A top-K of 1 means the next selected token is the most probable
     * among all tokens in the model's vocabulary (also called greedy decoding), while a top-K of 3 means that the next token
     * is selected from among the 3 most probable using the temperature.
     * <p>
     * Range: 1 - 40
     * <p>
     * Default: 40
     */
    OptionalInt topK();

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
     * Thinking related configuration
     */
    ThinkingConfig thinking();

    interface ThinkingConfig {

        /**
         * The thinking budget guides the model on the number of thinking tokens to use.
         * A higher token count generally allows for more detailed reasoning.
         * Setting the thinkingBudget to -1 turns on dynamic thinking.
         * Setting it to 0 disables thinking.
         */
        OptionalInt thinkingBudget();

        /**
         * The thinking level to use. This is the recommended parameter for Gemini 3.x models.
         * Allowed values are {@code "MINIMAL"}, {@code "LOW"}, {@code "MEDIUM"}, {@code "HIGH"}.
         * Cannot be used together with {@code thinkingBudget}.
         */
        Optional<String> thinkingLevel();
    }
}
