package io.quarkiverse.langchain4j.ollama.runtime.config;

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
     * The temperature of the model. Increasing the temperature will make the model answer with
     * more variability. A lower temperature will make the model answer more conservatively.
     */
    @WithDefault("${quarkus.langchain4j.temperature:0.8}")
    Double temperature();

    /**
     * Maximum number of tokens to predict when generating text
     */
    OptionalInt numPredict();

    /**
     * Sets the stop sequences to use. When this pattern is encountered the LLM will stop generating text and return
     */
    Optional<List<String>> stop();

    /**
     * Works together with top-k. A higher value (e.g., 0.95) will lead to more diverse text, while a lower value (e.g., 0.5)
     * will generate more focused and conservative text
     */
    @WithDefault("0.9")
    Double topP();

    /**
     * Reduces the probability of generating nonsense. A higher value (e.g. 100) will give more diverse answers, while a lower
     * value (e.g. 10) will be more conservative
     */
    @WithDefault("40")
    Integer topK();

    /**
     * With a static number the result is always the same. With a random number the result varies
     * Example:
     *
     * <pre>
     * {@code
     * Random random = new Random();
     * int x = random.nextInt(Integer.MAX_VALUE);
     * }
     * </pre>
     */
    Optional<Integer> seed();

    /**
     * The format to return a response in. Format can be {@code json} or a JSON schema.
     */
    Optional<String> format();

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
     * Additional model options for advanced configuration.
     */
    ModelOptionsConfig modelOptions();

    @ConfigGroup
    interface ModelOptionsConfig {

        /**
         * Enable thinking mode for models that support it (e.g., Qwen3).
         * When enabled, the model will output its reasoning process.
         */
        Optional<Boolean> think();

        /**
         * Whether to return thinking/reasoning content in the response.
         */
        Optional<Boolean> returnThinking();

        /**
         * Sets the size of the context window used to generate the next token.
         */
        OptionalInt numCtx();

        /**
         * The last N tokens to penalize. Higher values will penalize more.
         */
        OptionalInt repeatLastN();

        /**
         * The penalty for repeated tokens. Higher values will penalize repetition more.
         */
        OptionalDouble repeatPenalty();

        /**
         * Enable Mirostat sampling for controlling perplexity.
         * 0 = disabled, 1 = Mirostat, 2 = Mirostat 2.0.
         */
        OptionalInt mirostat();

        /**
         * Controls the learning rate of Mirostat. Lower values result in slower adjustments.
         */
        OptionalDouble mirostatEta();

        /**
         * Controls the target perplexity for Mirostat. Lower values produce more focused outputs.
         */
        OptionalDouble mirostatTau();

        /**
         * Sets the minimum probability relative to the most likely token.
         * Lower values will result in more diverse outputs.
         */
        OptionalDouble minP();

    }
}
