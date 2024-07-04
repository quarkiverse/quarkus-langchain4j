package io.quarkiverse.langchain4j.redis.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.List;
import java.util.Optional;

import io.quarkus.redis.datasource.search.DistanceMetric;
import io.quarkus.redis.datasource.search.VectorAlgorithm;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the Redis embedding store.
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.redis")
public interface RedisEmbeddingStoreConfig {

    /**
     * The dimension of the embedding vectors. This has to be the same as the dimension of vectors produced by
     * the embedding model that you use. For example, AllMiniLmL6V2QuantizedEmbeddingModel produces vectors of dimension 384.
     * OpenAI's text-embedding-ada-002 produces vectors of dimension 1536.
     */
    Long dimension();

    /**
     * Name of the index that will be used in Redis when searching for related embeddings.
     * If this index doesn't exist, it will be created.
     */
    @WithDefault("embedding-index")
    String indexName();

    /**
     * Names of fields that will store textual metadata associated with
     * embeddings.
     *
     * NOTE: Filtering based on textual metadata fields is not supported
     * at the moment.
     */
    Optional<List<String>> textualMetadataFields();

    /**
     * Names of fields that will store numeric metadata associated with
     * embeddings.
     */
    Optional<List<String>> numericMetadataFields();

    /**
     * Metric used to compute the distance between two vectors.
     */
    @WithDefault("COSINE")
    DistanceMetric distanceMetric();

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
     * <p>
     * If the configured prefix does not ends with {@code :}, it will be added automatically to follow the Redis convention.
     */
    @WithDefault("embedding:")
    String prefix();

    /**
     * Algorithm used to index the embedding vectors.
     */
    @WithDefault("HNSW")
    VectorAlgorithm vectorAlgorithm();

}
