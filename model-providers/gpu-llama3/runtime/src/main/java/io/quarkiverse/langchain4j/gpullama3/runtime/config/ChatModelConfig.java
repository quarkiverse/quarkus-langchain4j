package io.quarkiverse.langchain4j.gpullama3.runtime.config;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * What sampling temperature to use, between 0.0 and 1.0.
     */
    @ConfigDocDefault("0.3")
    @WithDefault("${quarkus.langchain4j.temperature}")
    OptionalDouble temperature();

    /**
     * The maximum number of tokens to generate in the completion.
     */
    OptionalInt maxTokens();
}