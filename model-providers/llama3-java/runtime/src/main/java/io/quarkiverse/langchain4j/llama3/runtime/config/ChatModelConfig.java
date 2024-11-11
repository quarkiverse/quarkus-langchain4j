package io.quarkiverse.langchain4j.llama3.runtime.config;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Temperature in [0,inf]
     */
    @ConfigDocDefault("0.1")
    @WithDefault("${quarkus.langchain4j.temperature}")
    OptionalDouble temperature();

    /**
     * Number of steps to run for < 0 = limited by context length
     */
    @ConfigDocDefault("512")
    OptionalInt maxTokens();

}
