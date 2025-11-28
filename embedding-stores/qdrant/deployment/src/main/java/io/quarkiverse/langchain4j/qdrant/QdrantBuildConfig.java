package io.quarkiverse.langchain4j.qdrant;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.qdrant")
public interface QdrantBuildConfig {
    /**
     * Configuration for DevServices. DevServices allows Quarkus to automatically start a database in dev and test mode.
     */
    DevServicesConfig devservices();

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
