package io.quarkiverse.langchain4j.pgvector.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface PgVectorNamedStoreBuildTimeConfig {

    /**
     * The name of the configured Postgres datasource to use for this store. If not set,
     * the default datasource from the Agroal extension will be used.
     */
    @WithDefault("<default>")
    Optional<String> datasource();
}
