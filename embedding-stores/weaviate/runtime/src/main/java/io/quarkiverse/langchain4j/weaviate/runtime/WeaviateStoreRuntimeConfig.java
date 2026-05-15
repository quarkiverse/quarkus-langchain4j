package io.quarkiverse.langchain4j.weaviate.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface WeaviateStoreRuntimeConfig {

    /**
     * The Weaviate API key to authenticate with.
     */
    Optional<String> apiKey();

    /**
     * The scheme, e.g. "https" of cluster URL. Find it under Details of your Weaviate cluster.
     */
    @WithDefault("http")
    String scheme();

    /**
     * The URL of the Weaviate server.
     */
    @WithDefault("localhost")
    String host();

    /**
     * The port of the Weaviate server. Defaults to 8080
     */
    @WithDefault("8080")
    Integer port();

    /**
     * gRPC configuration.
     */
    Grpc grpc();

    /**
     * The object class you want to store, e.g. "MyGreatClass". Must start from an uppercase letter.
     */
    @WithDefault("Default")
    String objectClass();

    /**
     * The name of the field that contains the text of a {@link dev.langchain4j.data.segment.TextSegment}. Default is
     * "text"
     */
    @WithDefault("text")
    String textFieldName();

    /**
     * If true (default), then {@code WeaviateEmbeddingStore} will generate a hashed ID based on
     * provided text segment, which avoids duplicated entries in DB.
     * If false, then random ID will be generated.
     */
    @WithDefault("false")
    Boolean avoidDups();

    /**
     * Consistency level: ONE, QUORUM (default) or ALL.
     */
    @WithDefault("QUORUM")
    ConsistencyLevel consistencyLevel();

    /**
     * Metadata configuration.
     */
    Metadata metadata();

    @ConfigGroup
    interface Grpc {

        /**
         * The gRPC port of the Weaviate server. Defaults to 50051
         */
        @WithDefault("50051")
        Integer port();

        /**
         * The gRPC connection is secured.
         */
        @WithDefault("false")
        Boolean secure();

        /**
         * Use gRPC instead of http for batch inserts only.
         * Will still be used for search.
         */
        @WithDefault("false")
        Boolean useForInserts();
    }

    enum ConsistencyLevel {
        ONE,
        QUORUM,
        ALL
    }

    @ConfigGroup
    interface Metadata {

        /**
         * Metadata keys that should be persisted.
         * The default in Weaviate [], however it is required to specify at least one for the EmbeddingStore to work.
         * Thus, we use "tags" as default
         */
        @WithDefault("tags")
        List<String> keys();

        /**
         * The name of the field where {@link dev.langchain4j.data.segment.Metadata} entries are stored
         */
        @WithDefault("_metadata")
        String fieldName();
    }
}
