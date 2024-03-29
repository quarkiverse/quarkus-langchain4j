package io.quarkiverse.langchain4j.infinispan.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the Infinispan embedding store.
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.infinispan")
public interface InfinispanEmbeddingStoreConfig {

    /**
     * The dimension of the embedding vectors. This has to be the same as the dimension of vectors produced by
     * the embedding model that you use. For example, AllMiniLmL6V2QuantizedEmbeddingModel produces vectors of dimension 384.
     * OpenAI's text-embedding-ada-002 produces vectors of dimension 1536.
     */
    Long dimension();

    /**
     * Name of the cache that will be used in Infinispan when searching for related embeddings.
     * If this cache doesn't exist, it will be created.
     */
    @WithDefault("embeddings-cache")
    String cacheName();

    /**
     * The maximum distance. The most distance between vectors is how close or far apart two embeddings are.
     */
    @WithDefault("3")
    Integer distance();

}
