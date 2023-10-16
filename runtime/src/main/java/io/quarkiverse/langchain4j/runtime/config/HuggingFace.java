package io.quarkiverse.langchain4j.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface HuggingFace {
    /**
     * Access token
     */
    Optional<String> accessToken();

    /**
     * Model Id
     */
    @WithDefault("tiiuae/falcon-7b-instruct")
    String modelId();

    /**
     * Timeout for API calls
     */
    @WithDefault("15s")
    Duration timeout();

    /**
     * Temperature
     */
    @WithDefault("1.0")
    Double temperature();

    /**
     * Max tokens
     */
    @WithDefault("16")
    Integer maxNewTokens();

    /**
     * Return full text
     */
    @WithDefault("false")
    Boolean returnFullText();

    /**
     * Wait for model
     */
    @WithDefault("true")
    Boolean waitForModel();
}
