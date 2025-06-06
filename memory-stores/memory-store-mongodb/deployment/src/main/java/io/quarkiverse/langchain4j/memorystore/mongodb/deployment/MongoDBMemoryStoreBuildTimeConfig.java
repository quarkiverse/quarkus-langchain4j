package io.quarkiverse.langchain4j.memorystore.mongodb.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.memorystore.mongodb")
public interface MongoDBMemoryStoreBuildTimeConfig {

    /**
     * The name of the MongoDB client to use. These clients are configured by means of the `mongodb` extension.
     * If unspecified, it will use the default MongoDB client.
     */
    Optional<String> clientName();

    /**
     * The name of the database to use.
     */
    @WithDefault("langchain4j")
    String database();

    /**
     * The name of the collection to use.
     */
    @WithDefault("chat_memory")
    String collection();
}
