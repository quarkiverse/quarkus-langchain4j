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
        return config.devservices().enabled();
    }

}
