package io.quarkiverse.langchain4j.lancedb.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.lancedb")
public interface LanceDbEmbeddingStoreBuildTimeConfig {

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
    Map<String, LanceDbNamedStoreBuildTimeConfig> namedConfig();

    @ConfigGroup
    interface DefaultStoreBuildTimeConfig {

        /**
         * Whether the default (unnamed) LanceDB embedding store should be enabled.
         * Set to {@code false} when you only want to use named stores.
         */
        @WithDefault("true")
        boolean defaultStoreEnabled();
    }
}
