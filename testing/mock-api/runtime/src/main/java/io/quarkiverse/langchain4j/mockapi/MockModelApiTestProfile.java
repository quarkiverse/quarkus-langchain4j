package io.quarkiverse.langchain4j.mockapi;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Enables the mock model API for a {@code @QuarkusTest} or {@code @QuarkusIntegrationTest} via {@code @TestProfile}.
 */
public class MockModelApiTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.langchain4j.mock-api.enabled", "true");
    }
}
