package io.quarkiverse.langchain4j.pinecone;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface PineconeNamedStoreBuildTimeConfig {

    /**
     * The index name for this named store.
     * This property serves as the build-time key that enables named store discovery.
     * If not set, the index name from the runtime configuration will be used.
     */
    @WithDefault("<default>")
    Optional<String> indexName();
}
