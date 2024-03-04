package io.quarkiverse.langchain4j.ollama.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use. According to <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md#model-names">Ollama
     * docs</a>,
     * the default value is {@code llama2}
     */
    @WithDefault("llama2")
    String modelId();

    /**
     * The temperature of the model. Increasing the temperature will make the model answer with
     * more variability. A lower temperature will make the model answer more conservatively.
     */
    @WithDefault("0.8")
    Double temperature();

    /**
     * Maximum number of tokens to predict when generating text
     */
    @WithDefault("128")
    Integer numPredict();

    /**
     * Sets the stop sequences to use. When this pattern is encountered the LLM will stop generating text and return
     */
    Optional<String> stop();

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
     * Random random = new Random();
     * int x = random.nextInt(Integer.MAX_VALUE);
     */
    Optional<Integer> seed();
}
