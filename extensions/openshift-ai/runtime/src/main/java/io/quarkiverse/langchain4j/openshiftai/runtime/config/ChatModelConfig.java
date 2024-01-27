package io.quarkiverse.langchain4j.openshiftai.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use
     */
    String modelId();
}
