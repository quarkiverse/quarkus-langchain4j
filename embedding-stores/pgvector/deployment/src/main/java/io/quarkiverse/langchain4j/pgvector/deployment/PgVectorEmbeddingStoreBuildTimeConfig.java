package io.quarkiverse.langchain4j.pgvector.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.pgvector")
public interface PgVectorEmbeddingStoreBuildTimeConfig {

    /**
     * Default store build-time config.
     */
    @WithParentName
    DefaultStoreBuildTimeConfig defaultConfig();

    /**
     * Named store configurations.
     */
    @ConfigDocSection
    @ConfigDocMapKey("store-name")
    @WithParentName
    @WithDefaults
    Map<String, PgVectorNamedStoreBuildTimeConfig> namedConfig();

    @ConfigGroup
    interface DefaultStoreBuildTimeConfig {

        /**
         * Whether the default (unnamed) PG Vector embedding store should be enabled.
         * Set to {@code false} when you only want to use named stores.
         */
        @WithDefault("true")
        boolean defaultStoreEnabled();

        /**
         * The name of the configured Postgres datasource to use for the default store. If not set,
         * the default datasource from the Agroal extension will be used.
         */
        Optional<String> datasource();
    }
}
