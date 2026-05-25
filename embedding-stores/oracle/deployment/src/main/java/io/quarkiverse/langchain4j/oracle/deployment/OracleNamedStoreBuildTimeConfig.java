package io.quarkiverse.langchain4j.oracle.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface OracleNamedStoreBuildTimeConfig {

    /**
     * Whether this named Oracle embedding store should be enabled.
     * Set to {@code false} to skip bean creation for this named store while keeping its configuration.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The name of the configured Oracle datasource to use for this named store.
     * If not set, the default datasource from the Agroal extension will be used.
     */
    @WithDefault("<default>")
    Optional<String> datasource();
}
