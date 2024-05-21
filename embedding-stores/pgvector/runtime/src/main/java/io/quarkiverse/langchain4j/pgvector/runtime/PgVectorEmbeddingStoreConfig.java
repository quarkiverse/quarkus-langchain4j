package io.quarkiverse.langchain4j.pgvector.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.pgvector")
public interface PgVectorEmbeddingStoreConfig {

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
    Integer dimension();

    /**
     * Use index or not
     */
    @WithDefault("false")
    Boolean useIndex();

    /**
     *
     * index size
     */
    @WithDefault("0")
    Integer indexListSize();

    /**
     * Create table or not
     */
    @WithDefault("true")
    Boolean createTable();

    /**
     * Drop table or not
     */
    @WithDefault("false")
    Boolean dropTableFirst();

}
