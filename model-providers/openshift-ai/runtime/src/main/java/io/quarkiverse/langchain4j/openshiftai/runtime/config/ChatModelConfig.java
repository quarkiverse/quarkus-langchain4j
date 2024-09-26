package io.quarkiverse.langchain4j.openshiftai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use
     */
    @WithDefault("dummy") // TODO: this is set to a dummy value because otherwise Smallrye Config cannot give a proper error for named models
    String modelId();

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
}
