package io.quarkiverse.langchain4j.opensearch.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;
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
@ConfigMapping(prefix = "quarkus.langchain4j.opensearch")
public interface OpenSearchEmbeddingStoreBuildTimeConfig {

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
    Map<String, OpenSearchNamedStoreBuildTimeConfig> namedConfig();

    /**
     * Configuration for Dev Services. Dev Services allows Quarkus to automatically start an
     * OpenSearch instance in dev and test mode.
     */
    OpenSearchDevServicesBuildTimeConfig devservices();

    @ConfigGroup
    interface DefaultStoreBuildTimeConfig {

        /**
         * Whether the default (unnamed) OpenSearch embedding store should be enabled.
         * Set to {@code false} when you only want to use named stores.
         */
        @WithDefault("true")
        boolean defaultStoreEnabled();
    }

    @ConfigGroup
    interface OpenSearchDevServicesBuildTimeConfig {

        /**
         * If Dev Services has been explicitly enabled or disabled. Dev Services is generally
         * enabled by default, unless there is an existing configuration present.
         * <p>
         * When Dev Services is enabled Quarkus will attempt to automatically configure and start
         * an OpenSearch instance when running in Dev or Test mode and when Docker is running.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * The container image name to use for the OpenSearch dev service.
         */
        @WithDefault("opensearchproject/opensearch:2.10.0")
        String imageName();

        /**
         * Optional fixed port the dev service will listen to.
         * <p>
         * If not defined, the port will be chosen randomly.
         */
        OptionalInt port();

        /**
         * Indicates if the OpenSearch instance managed by Quarkus Dev Services is shared.
         * When shared, Quarkus looks for running containers using label-based service discovery.
         * If a matching container is found, it is used, and so a second one is not started.
         * Otherwise, Dev Services for OpenSearch starts a new container.
         * <p>
         * The discovery uses the {@code quarkus-dev-service-opensearch} label.
         * The value is configured using the {@code service-name} property.
         * <p>
         * Container sharing is only used in dev mode.
         */
        @WithDefault("true")
        boolean shared();

        /**
         * The value of the {@code quarkus-dev-service-opensearch} label attached to the started
         * container. This property is used when {@code shared} is set to {@code true}.
         * In this case, before starting a container, Dev Services for OpenSearch looks for a
         * container with the {@code quarkus-dev-service-opensearch} label set to the configured
         * value. If found, it will use this container instead of starting a new one. Otherwise,
         * it starts a new container with the {@code quarkus-dev-service-opensearch} label set to
         * the specified value.
         * <p>
         * This property is used when you need multiple shared OpenSearch instances.
         */
        @WithDefault("opensearch")
        String serviceName();

        /**
         * Environment variables that are passed to the container.
         */
        Map<String, String> containerEnv();
    }
}
