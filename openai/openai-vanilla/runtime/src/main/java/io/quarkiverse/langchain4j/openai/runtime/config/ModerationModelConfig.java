package io.quarkiverse.langchain4j.openai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ModerationModelConfig {

    /**
     * Model name to use
     */
    @WithDefault("text-moderation-latest")
    String modelName();

    /**
     * Whether moderation model requests should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.openai.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether moderation model responses should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.openai.log-responses}")
    Optional<Boolean> logResponses();
}
