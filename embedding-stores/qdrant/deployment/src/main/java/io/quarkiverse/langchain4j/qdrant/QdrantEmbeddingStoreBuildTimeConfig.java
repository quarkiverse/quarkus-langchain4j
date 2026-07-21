package io.quarkiverse.langchain4j.qdrant;

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
@ConfigMapping(prefix = "quarkus.langchain4j.qdrant")
public interface QdrantEmbeddingStoreBuildTimeConfig {

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
    Map<String, QdrantNamedStoreBuildTimeConfig> namedConfig();

    @ConfigGroup
    interface DefaultStoreBuildTimeConfig {

        /**
         * Whether the default (unnamed) Qdrant embedding store should be enabled.
         * Set to {@code false} when you only want to use named stores.
         */
        @WithDefault("true")
        boolean defaultStoreEnabled();

        /**
         * The name of the Qdrant client to use. These clients are configured by means of the `quarkus-qdrant` extension.
         * If not set, the default Qdrant client will be used.
         */
        Optional<String> clientName();

        /**
         * The name of the Qdrant collection to use.
         */
        @WithDefault("default")
        String collectionName();

        /**
         * The field name of the text segment in the payload.
         */
        @WithDefault("text_segment")
        String payloadTextKey();
    }
}
