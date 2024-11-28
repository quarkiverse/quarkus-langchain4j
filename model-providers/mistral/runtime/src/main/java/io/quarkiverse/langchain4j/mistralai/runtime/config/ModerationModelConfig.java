package io.quarkiverse.langchain4j.mistralai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.smallrye.config.WithDefault;

public interface ModerationModelConfig {

    /**
     * Model name to use
     */
    @WithDefault("mistral-moderation-latest")
    String modelName();

    /**
     * Whether moderation model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether moderation model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

}
