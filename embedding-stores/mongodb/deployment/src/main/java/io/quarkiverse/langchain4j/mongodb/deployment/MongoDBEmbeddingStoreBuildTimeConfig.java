package io.quarkiverse.langchain4j.mongodb.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.mongodb")
public interface MongoDBEmbeddingStoreBuildTimeConfig {

    /**
     * The name of the MongoDB client to use.
     * If not specified, the default client will be used.
     */
    Optional<String> clientName();
}
