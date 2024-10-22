package io.quarkiverse.langchain4j.llama3.runtime.config;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * TODO
     */
    OptionalDouble temperature();

    /**
     * TODO
     */
    OptionalInt maxTokens();

}
