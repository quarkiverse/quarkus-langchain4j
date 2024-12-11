package io.quarkiverse.langchain4j.ollama.deployment.devservices;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

/**
 * Allows configuring the Ollama dev services
 */
@ConfigGroup
public interface OllamaDevServicesBuildConfig {
    /**
     * Default docker image name.
     */
    String OLLAMA_IMAGE = "ollama/ollama:latest";

    /**
     * If Dev Services for Ollama has been explicitly enabled or disabled. Dev Services are generally enabled
     * by default, unless there is an existing configuration present.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The Ollama container image to use.
     */
    @WithDefault(OLLAMA_IMAGE)
    String imageName();
}
