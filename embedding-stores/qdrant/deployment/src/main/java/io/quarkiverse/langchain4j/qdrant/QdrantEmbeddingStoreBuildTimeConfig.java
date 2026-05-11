package io.quarkiverse.langchain4j.qdrant;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

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

    /**
     * Configuration for DevServices. DevServices allows Quarkus to automatically start a database in dev and test mode.
     */
    DevServicesConfig devservices();

    @ConfigGroup
    interface DefaultStoreBuildTimeConfig {

        /**
         * Whether the default (unnamed) Qdrant embedding store should be enabled.
         * Set to {@code false} when you only want to use named stores.
         */
        @WithDefault("true")
        boolean defaultStoreEnabled();
    }

    @ConfigGroup
    interface DevServicesConfig {

        /**
         * Whether Dev Services for Qdrant are enabled or not.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Container image for Qdrant.
         */
        @WithDefault("docker.io/qdrant/qdrant:v1.16-unprivileged")
        String qdrantImageName();

        /**
         * Optional fixed port the Qdrant dev service will listen to.
         * If not defined, the port will be chosen randomly.
         */
        OptionalInt port();

        /**
         * Indicates if the Dev Service containers managed by Quarkus for Qdrant are shared.
         */
        @WithDefault("true")
        boolean shared();

        /**
         * Service label to apply to created Dev Services containers.
         */
        @WithDefault("qdrant")
        String serviceName();

        /**
         * The Qdrant collection configuration.
         */
        Optional<CollectionConfig> collection();

        interface CollectionConfig {
            /**
             * The vector parameters.
             */
            VectorParamsConfig vectorParams();
        }

        interface VectorParamsConfig {
            /**
             * Distance function used for comparing vectors
             */
            Distance distance();

            /**
             * Size of the vectors
             */
            long size();
        }
    }
}
