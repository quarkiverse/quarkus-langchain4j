package io.quarkiverse.langchain4j.qdrant.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface QdrantStoreRuntimeConfig {

    /**
     * The URL of the Qdrant server.
     */
    Optional<String> host();

    /**
     * The gRPC port of the Qdrant server. Defaults to 6334
     */
    @WithDefault("6334")
    Integer port();

    /**
     * The Qdrant API key to authenticate with.
     */
    Optional<String> apiKey();

    /**
     * Whether to use TLS(HTTPS). Defaults to false.
     */
    @WithDefault("false")
    boolean useTls();

    /**
     * The field name of the text segment in the payload. Defaults to "text_segment"
     */
    @WithDefault("text_segment")
    String payloadTextKey();

    /**
     * The collection configuration.
     */
    CollectionConfig collection();

    interface CollectionConfig {
        /**
         * The name of the collection.
         */
        @WithDefault("default")
        String name();
    }
}
