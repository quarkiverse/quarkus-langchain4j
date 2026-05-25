package io.quarkiverse.langchain4j.infinispan;

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
@ConfigMapping(prefix = "quarkus.langchain4j.infinispan")
public interface InfinispanEmbeddingStoreBuildTimeConfig {

    @WithParentName
    DefaultStoreBuildTimeConfig defaultConfig();

    /**
     * Named store configurations.
     */
    @ConfigDocSection
    @ConfigDocMapKey("store-name")
    @WithParentName
    @WithDefaults
    Map<String, InfinispanNamedStoreBuildTimeConfig> namedConfig();

    @ConfigGroup
    interface DefaultStoreBuildTimeConfig {

        /**
         * Whether the default (unnamed) Infinispan embedding store should be enabled.
         * Set to {@code false} when you only want to use named stores.
         */
        @WithDefault("true")
        boolean defaultStoreEnabled();

        /**
         * The name of the Infinispan client to use. These clients are configured by means of the `infinispan-client`
         * extension.
         * If unspecified, it will use the default Infinispan client.
         */
        Optional<String> clientName();
    }

}
