package io.quarkiverse.langchain4j.redis;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Ensure we set the Redis stack image name so users don't have to
 */
public class DevServicesConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(final SmallRyeConfigBuilder builder) {
        // use a priority of 50 to make sure that this is overridable by any of the standard methods
        builder.withSources(
                new PropertiesConfigSource(Map.of("quarkus.redis.devservices.image-name", "redis/redis-stack:latest"),
                        "quarkus-langchain4j-redis", 50));
    }
}
