package io.quarkiverse.langchain4j.mockapi.deployment;

import java.util.function.BooleanSupplier;

import io.quarkiverse.langchain4j.deployment.config.LangChain4jBuildConfig;

public class MockApiEnabled implements BooleanSupplier {

    private final LangChain4jBuildConfig config;

    MockApiEnabled(LangChain4jBuildConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.mockApi().enabled();
    }
}
