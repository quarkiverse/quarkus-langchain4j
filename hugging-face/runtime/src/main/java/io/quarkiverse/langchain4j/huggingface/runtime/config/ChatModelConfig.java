package io.quarkiverse.langchain4j.huggingface.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use
     */
    @WithDefault("tiiuae/falcon-7b-instruct")
    String modelId();

    /**
     * TODO
     */
    @WithDefault("1.0")
    Double temperature();

    /**
     * TODO
     */
    Optional<Integer> maxNewTokens();

    /**
     * TODO
     */
    @WithDefault("false")
    Boolean returnFullText();

    /**
     * TODO
     */
    @WithDefault("true")
    Boolean waitForModel();
}
