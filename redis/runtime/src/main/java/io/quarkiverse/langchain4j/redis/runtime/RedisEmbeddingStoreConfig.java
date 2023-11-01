package io.quarkiverse.langchain4j.redis.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import java.util.List;
import java.util.Optional;

import io.quarkiverse.langchain4j.redis.MetricType;
import io.quarkiverse.langchain4j.redis.VectorAlgorithm;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the Redis embedding store.
 */
@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.redis")
public interface RedisEmbeddingStoreConfig {

    /**
     * The name of the Redis client to use. These clients are configured by means of the `redis-client` extension.
     * If unspecified, it will use the default Redis client.
     */
    Optional<String> clientName();

    /**
     * The dimension of the embedding vectors. This has to be the same as the dimension of vectors produced by
     * the embedding model that you use. For example, AllMiniLmL6V2QuantizedEmbeddingModel produces vectors of dimension 384.
     * OpenAI's text-embedding-ada-002 produces vectors of dimension 1536.
     *
     */
    Long dimension();

    /**
     * Name of the index that will be used in Redis when searching for related embeddings.
     * If this index doesn't exist, it will be created.
     */
    @WithDefault("embedding-index")
    String indexName();

    /**
     * Names of extra fields that will be stored in Redis along with the
     * embedding vectors. This corresponds to keys in the
     * `dev.langchain4j.data.document.Metadata` map. Storing embeddings with
     * metadata fields unlisted here is possible, but these fields will then
     * not be present in the returned EmbeddingMatch objects.
     */
    Optional<List<String>> metadataFields();

    /**
     * Metric used to compute the distance between two vectors.
     */
    @WithDefault("COSINE")
    MetricType metricType();

    /**
     * Name of the key that will be used to store the embedding vector.
     */
    @WithDefault("vector")
    String vectorFieldName();

    /**
     * Name of the key that will be used to store the embedded text.
     */
    @WithDefault("scalar")
    String scalarFieldName();

    /**
     * Prefix to be applied to all keys by the embedding store. Embeddings are stored in Redis
     * under a key that is the concatenation of this prefix and the embedding ID.
     */
    @WithDefault("embedding:")
    String prefix();

    /**
     * Algorithm used to index the embedding vectors.
     */
    @WithDefault("HNSW")
    VectorAlgorithm vectorAlgorithm();

}
