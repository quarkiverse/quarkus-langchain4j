package io.quarkiverse.langchain4j.openshiftai.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use
     */
    @WithDefault("dummy") // TODO: this is set to a dummy value because otherwise Smallrye Config cannot give a proper error for named models
    String modelId();
}
