package io.quarkiverse.langchain4j.milvus;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.milvus")
public interface MilvusBuildConfig {

    /**
     * Configuration for DevServices. DevServices allows Quarkus to automatically start a database in dev and test mode.
     */
    MilvusDevServicesBuildTimeConfig devservices();

    @ConfigGroup
    interface MilvusDevServicesBuildTimeConfig {

        /**
         * Whether Dev Services for Milvus are enabled or not.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Container image for Milvus.
         */
        @WithDefault("docker.io/milvusdb/milvus:v2.3.4")
        String milvusImageName();

        /**
         * Optional fixed port the Milvus dev service will listen to.
         * If not defined, the port will be chosen randomly.
         */
        OptionalInt port();

        /**
         * Indicates if the Dev Service containers managed by Quarkus for Milvus are shared.
         */
        @WithDefault("true")
        boolean shared();

        /**
         * Service label to apply to created Dev Services containers.
         */
        @WithDefault("milvus")
        String serviceName();

    }
}
