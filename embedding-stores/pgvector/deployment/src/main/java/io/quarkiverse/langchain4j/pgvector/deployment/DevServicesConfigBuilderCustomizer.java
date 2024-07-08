package io.quarkiverse.langchain4j.pgvector.deployment;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Ensure we set the pgvector stack image name so users don't have to
 */
public class DevServicesConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(final SmallRyeConfigBuilder builder) {
        // use a priority of 50 to make sure that this is overridable by any of the
        // standard methods
        builder.withSources(new PropertiesConfigSource(
                Map.of("quarkus.datasource.devservices.image-name", "pgvector/pgvector:pg17"),
                "quarkus-langchain4j-pgvector", 50));
    }
}
