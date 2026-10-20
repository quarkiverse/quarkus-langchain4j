package io.quarkiverse.langchain4j.deployment.devservice;

import java.util.function.BooleanSupplier;

import io.quarkiverse.langchain4j.deployment.config.LangChain4jBuildConfig;

/**
 * Supplier that can be used to only run build steps
 * if the LangChain4j DevServices have been enabled.
 */
public class Langchain4jDevServicesEnabled implements BooleanSupplier {

    private final LangChain4jBuildConfig config;

    Langchain4jDevServicesEnabled(LangChain4jBuildConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        // the mock model API points every model at itself, so a model dev service (an Ollama container, or pulling
        // models into a local Ollama) would only be started for nothing
        return !config.mockApi().enabled() && config.devservices().enabled();
    }

}
