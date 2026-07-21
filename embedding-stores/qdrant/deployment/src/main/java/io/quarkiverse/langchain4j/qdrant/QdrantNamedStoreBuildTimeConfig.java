package io.quarkiverse.langchain4j.qdrant;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface QdrantNamedStoreBuildTimeConfig {

    /**
     * The name of the Qdrant client to use. These clients are configured by means of the `quarkus-qdrant` extension.
     * If not set, the default Qdrant client will be used.
     */
    @WithDefault("<default>")
    Optional<String> clientName();

    /**
     * The name of the Qdrant collection to use.
     */
    @WithDefault("default")
    String collectionName();

    /**
     * The field name of the text segment in the payload.
     */
    @WithDefault("text_segment")
    String payloadTextKey();
}
