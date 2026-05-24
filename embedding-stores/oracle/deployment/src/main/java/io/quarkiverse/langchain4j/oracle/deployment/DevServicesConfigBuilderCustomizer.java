package io.quarkiverse.langchain4j.oracle.deployment;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Ensure we set the Oracle Free 23ai image name so users don't have to.
 */
public class DevServicesConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(final SmallRyeConfigBuilder builder) {
        // use a priority of 50 to make sure that this is overridable by any of the standard methods
        builder.withSources(
                new PropertiesConfigSource(Map.of("quarkus.datasource.devservices.image-name", "gvenzl/oracle-free:23-slim"),
                        "quarkus-langchain4j-oracle", 50));
    }
}
