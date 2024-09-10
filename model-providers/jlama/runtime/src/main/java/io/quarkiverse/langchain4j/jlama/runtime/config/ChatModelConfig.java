package io.quarkiverse.langchain4j.jlama.runtime.config;

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
    @WithDefault("tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-Q4")
    String modelName();

    /**
     * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8
     * will make the output more random, while lower values like 0.2 will make it
     * more focused and deterministic.
     * <p>
     * It is generally recommended to set this or the {@code top-k} property but not
     * both.
     */
    @ConfigDocDefault("0.3f")
    OptionalDouble temperature();

    /**
     * The maximum number of tokens to generate in the completion.
     * <p>
     * The token count of your prompt plus {@code max_tokens} cannot exceed the
     * model's context length
     */
    OptionalInt maxTokens();

}
