package io.quarkiverse.langchain4j.qdrant.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.qdrant")
public interface QdrantRuntimeConfig {

    /**
     * The URL of the Qdrant server.
     */
    String host();

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
        String name();
    }
}
