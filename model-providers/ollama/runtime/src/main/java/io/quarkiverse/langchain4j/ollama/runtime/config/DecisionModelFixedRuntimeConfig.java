package io.quarkiverse.langchain4j.ollama.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface DecisionModelFixedRuntimeConfig {

    /**
     * Model to use. Ollama runs <a href="https://docs.ollama.com/capabilities/decision">decision models</a> such as
     * {@code nimble}, {@code tev1} and {@code clef} locally, the model must be pulled first ({@code ollama pull nimble})
     */
    @WithDefault("nimble")
    String modelId();
}
