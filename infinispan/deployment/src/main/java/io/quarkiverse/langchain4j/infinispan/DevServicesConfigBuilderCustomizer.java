package io.quarkiverse.langchain4j.infinispan;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Ensure we set the Infinispan image name so users don't have to.
 * <p>
 * This just temporary until Quarkus itself moves to Infinispan 15.
 */
public class DevServicesConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(final SmallRyeConfigBuilder builder) {
        // use a priority of 50 to make sure that this is overridable by any of the standard methods
        builder.withSources(
                new PropertiesConfigSource(Map.of("quarkus.redis.devservices.image-name", "quay.io/infinispan/server:15.0"),
                        "quarkus-langchain4j-infinispan", 50));
    }
}
