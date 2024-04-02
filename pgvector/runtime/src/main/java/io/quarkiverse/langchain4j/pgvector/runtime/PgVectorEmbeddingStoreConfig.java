package io.quarkiverse.langchain4j.pgvector.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
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

    /**
     * Metadata configuration.
     */
    MetadataConfig metadata();

    @ConfigGroup
    interface MetadataConfig extends dev.langchain4j.store.embedding.pgvector.MetadataConfig {
        /**
         * Metadata type:
         * <ul>
         * <li>COLUMNS: for static metadata, when you know in advance the list of metadata
         * <li>JSON: For dynamic metadata, when you don't know the list of metadata that will be used.
         * <li>JSONB: Same as JSON, but stored in a binary way. Optimized for query on large dataset.
         * </ul>
         * <p>
         * Default value: JSON
         */
        @WithDefault("JSON")
        String type();

        /**
         * Metadata Definition: SQL definition of metadata field(s).
         * By default, "metadata JSON NULL" configured for JSON metadata type
         * Ex: condominium_id uuid null, user uuid null
         */
        @WithDefault("metadata JSON NULL")
        List<String> definition();

        /**
         * Metadata Indexes, list of fields to use as index
         * example:
         * <ul>
         * <li>JSON: metadata or (metadata->'key'), (metadata->'name'), (metadata->'age')
         * <li>JSONB: (metadata_b->'key'), (metadata_b->'name'), (metadata_b->'age')
         * <li>COLUMNS: key, name, age
         * </ul>
         */
        Optional<List<String>> indexes();

        /**
         * Index Type:
         * <ul>
         * <li>BTREE (default)
         * <li>GIN
         * <li>... postgres indexes
         * </ul>
         */
        @WithDefault("BTREE")
        String indexType();
    }
}
