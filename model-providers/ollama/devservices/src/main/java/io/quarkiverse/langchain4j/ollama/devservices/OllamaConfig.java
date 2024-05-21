package io.quarkiverse.langchain4j.ollama.devservices;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Allows configuring the OLlama Container
 */
@ConfigMapping(prefix = "quarkus.langchain4j.ollama.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface OllamaConfig {

    /**
     * Default docker image name.
     */
    String OLLAMA_IMAGE = "ollama/ollama:latest";

    String ORCA_MINI_MODEL = "orca-mini";

    /**
     * If Dev Services for OLlama has been explicitly enabled or disabled. Dev Services are generally enabled
     * by default, unless there is an existing configuration present.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The OLlama container image to use.
     */
    @WithDefault(OLLAMA_IMAGE)
    String imageName();

    /**
     * Model to install.
     */
    @WithDefault(ORCA_MINI_MODEL)
    String model();

}
