package io.quarkiverse.langchain4j.hibernate.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.hibernate-orm-dynamic")
public interface HibernateDynamicEmbeddingStoreBuildTimeConfig {

    /**
     * The name of the configured datasource to use for this store. If not set,
     * the default datasource from the Agroal extension will be used.
     */
    Optional<String> datasource();

    /**
     * The table name for storing embeddings
     */
    @WithDefault("embeddings")
    String table();

    /**
     * The dimension of the embedding vectors. This has to be the same as the dimension of vectors produced by
     * the embedding model that you use. For example, AllMiniLmL6V2QuantizedEmbeddingModel produces vectors of dimension 384.
     * OpenAI's text-embedding-ada-002 produces vectors of dimension 1536.
     */
    Optional<Integer> dimension();

    /**
     * Use index or not
     */
    @WithDefault("false")
    boolean createIndex();

    /**
     *
     * index options
     */
    Optional<String> indexOptions();

    /**
     * index type.
     */
    Optional<String> indexType();

    /**
     * Whether the table should be created if not already existing.
     */
    @WithDefault("false")
    boolean createTable();

    /**
     * Whether the table should be dropped prior to being created.
     */
    @WithDefault("false")
    boolean dropTableFirst();

    /**
     * The distance function to use.
     */
    @WithDefault("COSINE")
    DistanceFunction distanceFunction();

}
