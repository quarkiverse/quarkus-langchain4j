package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface SchemaConfig {

    /**
     * Create schema related settings.
     * <p>
     * This configuration is only required when using the {@code CreateSchemaService} class.
     */
    Optional<CreateSchemaConfig> create();

    /**
     * Improve schema related settings.
     */
    ImproveSchemaConfig improve();

    /**
     * Merge schema related settings.
     */
    MergeSchemaConfig merge();

    /**
     * Cluster schema related settings.
     */
    ClusterSchemaConfig cluster();
}
