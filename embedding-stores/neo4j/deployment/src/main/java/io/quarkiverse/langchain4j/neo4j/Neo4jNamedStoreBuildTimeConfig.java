package io.quarkiverse.langchain4j.neo4j;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface Neo4jNamedStoreBuildTimeConfig {

    /**
     * Whether this named Neo4j embedding store should be enabled.
     * Set to {@code false} to skip bean creation for this named store while keeping its configuration.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The name of the Neo4j database this store connects to.
     * Defaults to the {@code neo4j} database.
     * <p>
     * Connecting to a database other than the default {@code neo4j} requires Neo4j Enterprise Edition.
     * On Community Edition, isolate named stores via distinct {@code label}, {@code index-name}
     * and {@code embedding-property} values within the default database instead.
     */
    @WithDefault("<default>")
    Optional<String> databaseName();
}
