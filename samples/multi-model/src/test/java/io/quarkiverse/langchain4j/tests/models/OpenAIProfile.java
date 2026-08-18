package io.quarkiverse.langchain4j.tests.models;

import io.quarkus.test.junit.QuarkusTestProfile;

public class OpenAIProfile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
        return "openai";
    }
}
