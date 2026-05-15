package io.quarkiverse.langchain4j.weaviate.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface WeaviateNamedStoreBuildTimeConfig {

    /**
     * The object class used as the build-time discovery key for this named store.
     * Each named store is identified by its object class within the same Weaviate server.
     */
    @WithDefault("<default>")
    Optional<String> objectClass();
}
