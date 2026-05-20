package io.quarkiverse.langchain4j.infinispan.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

/**
 * Configuration of the Infinispan embedding store.
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.infinispan")
public interface InfinispanEmbeddingStoreConfig {

    @WithParentName
    InfinispanStoreRuntimeConfig defaultConfig();

}
