package io.quarkiverse.langchain4j.pgvector.deployment;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Ensure we set the pgvector stack image name so users don't have to.
 * And we configure jdbc connection with PG vector data type
 */
public class PgVectorConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(final SmallRyeConfigBuilder builder) {
        // use a priority of 50 to make sure that this is overridable by any of the standard methods
        builder.withSources(
                new PropertiesConfigSource(Map.of(
                        "quarkus.datasource.devservices.image-name", "pgvector/pgvector:pg16"
                // Weird behavior with this option: the first time the containers pg16 start,
                // and the first time we create a connection, the pv vector type is added,
                // but as the vector extension is not installed , the type is created with oid 0,
                // means it is considered as unknown.
                // For production we recommend to use sql script initialisation to add the extension
                // and create the table
                // For dev service a specific sql script will be triggered
                //"quarkus.datasource.jdbc.additional-jdbc-properties.datatype.vector", "com.pgvector.PGvector"
                ),
                        "quarkus-langchain4j-pgvector", 50));
    }
}
