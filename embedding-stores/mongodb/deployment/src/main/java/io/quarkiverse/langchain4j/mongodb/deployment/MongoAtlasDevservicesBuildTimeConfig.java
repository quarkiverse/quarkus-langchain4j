package io.quarkiverse.langchain4j.mongodb.deployment;

import java.util.Map;
import java.util.Optional;

import io.quarkus.mongodb.deployment.DevServicesBuildTimeConfig;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mongodb-atlas.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface MongoAtlasDevservicesBuildTimeConfig extends DevServicesBuildTimeConfig {

    /**
     * If DevServices has been explicitly enabled or disabled. DevServices is generally enabled by default, unless there is an
     * existing configuration present.
     * When DevServices is enabled Quarkus will attempt to automatically configure and start a database when running in Dev or
     * Test mode
     */
    @Override
    Optional<Boolean> enabled();

    /**
     * The container image name to use, for container based DevServices providers.
     */
    @Override
    @WithDefault("mongodb/mongodb-atlas-local:latest")
    Optional<String> imageName();

    /**
     * Optional fixed port the dev service will listen to.
     * <p>
     * If not defined, the port will be chosen randomly.
     */
    @Override
    Optional<Integer> port();

    /**
     * Generic properties that are added to the connection URL.
     */
    @Override
    @ConfigDocMapKey("property-key")
    Map<String, String> properties();

    /**
     * Environment variables that are passed to the container.
     */
    @Override
    @ConfigDocMapKey("environment-variable-name")
    Map<String, String> containerEnv();

    /**
     * Indicates if the MongoDB server managed by Quarkus Dev Services is shared.
     * When shared, Quarkus looks for running containers using label-based service discovery.
     * If a matching container is found, it is used, and so a second one is not started.
     * Otherwise, Dev Services for MongoDB starts a new container.
     * <p>
     * The discovery uses the {@code quarkus-dev-service-mongodb} label.
     * The value is configured using the {@code service-name} property.
     * <p>
     * Container sharing is only used in dev mode.
     */
    @Override
    @WithDefault("true")
    boolean shared();

    /**
     * The value of the {@code quarkus-dev-service-mongodb} label attached to the started container.
     * This property is used when {@code shared} is set to {@code true}.
     * In this case, before starting a container, Dev Services for MongoDB looks for a container with the
     * {@code quarkus-dev-service-mongodb} label
     * set to the configured value. If found, it will use this container instead of starting a new one. Otherwise it
     * starts a new container with the {@code quarkus-dev-service-mongodb} label set to the specified value.
     * <p>
     */
    @Override
    @WithDefault("mongodb")
    String serviceName();

}
