package io.quarkiverse.langchain4j.mongodb.deployment;

import java.util.Optional;

import io.quarkus.mongodb.deployment.DevServicesBuildTimeConfig;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mongodb-atlas.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface MongoAtlasDevservicesBuildTimeConfig extends DevServicesBuildTimeConfig {

    /**
     * The container image name to use, for container based DevServices providers.
     */
    @Override
    @WithDefault("mongodb/mongodb-atlas-local:latest")
    Optional<String> imageName();
}
