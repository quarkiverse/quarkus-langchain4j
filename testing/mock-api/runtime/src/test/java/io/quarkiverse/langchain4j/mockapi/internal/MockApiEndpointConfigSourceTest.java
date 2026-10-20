package io.quarkiverse.langchain4j.mockapi.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mockapi.internal.config.MockApiConfigSourceFactory;
import io.quarkiverse.langchain4j.mockapi.internal.config.MockApiEndpointConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class MockApiEndpointConfigSourceTest {

    private static final Map<String, String> OVERRIDES = Map.of(
            "quarkus.langchain4j.openai.base-url", "http://localhost:1234/v1",
            "quarkus.langchain4j.openai.m1.base-url", "http://localhost:1234/v1",
            "quarkus.langchain4j.azure-openai.m1.chat-model.endpoint",
            "http://localhost:1234/openai/deployments/mock-deployment");

    @Test
    void servesExactlyThePublishedKeys() {
        var src = new MockApiEndpointConfigSource(OVERRIDES);
        assertThat(src.getValue("quarkus.langchain4j.openai.base-url")).isEqualTo("http://localhost:1234/v1");
        assertThat(src.getValue("quarkus.langchain4j.openai.m1.base-url")).isEqualTo("http://localhost:1234/v1");
        assertThat(src.getValue("quarkus.langchain4j.openai.m2.base-url")).isNull();
        assertThat(src.getValue("quarkus.langchain4j.openai.api-key")).isNull();
        assertThat(src.getPropertyNames()).containsExactlyInAnyOrderElementsOf(OVERRIDES.keySet());
    }

    @Test
    void ordinalBeatsSystemProperties() {
        assertThat(new MockApiEndpointConfigSource(OVERRIDES).getOrdinal()).isGreaterThan(400);
    }

    @Test
    void formatAndParseRoundTrip() {
        assertThat(MockApiConfigSourceFactory.parse(MockApiConfigSourceFactory.format(OVERRIDES))).isEqualTo(OVERRIDES);
    }

    @Test
    void theOverridesBeatTheUsersUrl() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withDefaultValue("quarkus.langchain4j.openai.base-url", "https://api.openai.com/v1")
                .withSources(
                        new MockApiEndpointConfigSource(Map.of("quarkus.langchain4j.openai.base-url", "http://localhost:1/v1")))
                .build();
        assertThat(config.getValue("quarkus.langchain4j.openai.base-url", String.class)).isEqualTo("http://localhost:1/v1");
    }

    @Test
    void factoryIsInertWithoutOverrides() {
        SmallRyeConfig config = new SmallRyeConfigBuilder().withSources(new MockApiConfigSourceFactory()).build();
        assertThat(config.getOptionalValue("quarkus.langchain4j.openai.base-url", String.class)).isEmpty();
    }

    @Test
    void factoryReadsTheOverridesProperty() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withDefaultValue(MockApiConfigSourceFactory.OVERRIDES, MockApiConfigSourceFactory.format(OVERRIDES))
                .withSources(new MockApiConfigSourceFactory())
                .build();
        assertThat(config.getValue("quarkus.langchain4j.openai.m1.base-url", String.class))
                .isEqualTo("http://localhost:1234/v1");
    }
}
