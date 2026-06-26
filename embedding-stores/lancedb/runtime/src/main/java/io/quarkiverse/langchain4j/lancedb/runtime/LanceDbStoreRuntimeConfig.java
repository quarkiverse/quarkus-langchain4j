package io.quarkiverse.langchain4j.lancedb.runtime;

import java.util.Optional;

import io.quarkiverse.langchain4j.lancedb.LanceDbEmbeddingStore.DistanceType;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface LanceDbStoreRuntimeConfig {

    /**
     * The API key for authenticating with LanceDB Cloud or Enterprise.
     */
    Optional<String> apiKey();

    /**
     * The name of the LanceDB database to connect to.
     */
    Optional<String> database();

    /**
     * The endpoint URL for LanceDB Enterprise. If not set, LanceDB Cloud will be used.
     */
    Optional<String> endpoint();

    /**
     * The AWS region for LanceDB Cloud.
     */
    @WithDefault("us-east-1")
    String region();

    /**
     * The name of the table to use for storing embeddings.
     */
    @WithDefault("default")
    String tableName();

    /**
     * The dimension of the embeddings to be stored. Must match the dimension of the embedding model used.
     */
    Optional<Integer> dimension();

    /**
     * The distance metric for vector similarity search.
     */
    @WithDefault("l2")
    DistanceType distanceType();
}
