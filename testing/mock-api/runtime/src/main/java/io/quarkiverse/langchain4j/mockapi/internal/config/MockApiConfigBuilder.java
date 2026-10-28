package io.quarkiverse.langchain4j.mockapi.internal.config;

import io.quarkus.runtime.configuration.ConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilder;

public final class MockApiConfigBuilder implements ConfigBuilder {

    public MockApiConfigBuilder() {
    }

    @Override
    public SmallRyeConfigBuilder configBuilder(SmallRyeConfigBuilder builder) {
        return builder.withSources(new MockApiConfigSourceFactory());
    }
}
