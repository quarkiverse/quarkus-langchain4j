package io.quarkiverse.langchain4j.pgvector.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
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
     * Whether the table should be created if not already existing.
     */
    @WithDefault("true")
    Boolean createTable();

    /**
     * Whether the table should be dropped prior to being created.
     */
    @WithDefault("false")
    Boolean dropTableFirst();

    /**
     * Metadata configuration.
     */
    MetadataConfig metadata();

    @ConfigGroup
    interface MetadataConfig {
        // Could not extends dev.langchain4j.store.embedding.pgvector.MetadataConfig {
        // because of asciidoctor generating twice the same properties and build fail.
        /**
         * Metadata type:
         * <ul>
         * <li>COLUMN_PER_KEY: for static metadata, when you know in advance the list of metadata fields. In this case,
         * you should also override the `quarkus.langchain4j.pgvector.metadata.column-definitions` property to define the right
         * columns.
         * <li>COMBINED_JSON: For dynamic metadata, when you don't know the list of metadata fields that will be used.
         * <li>COMBINED_JSONB: Same as JSON, but stored in a binary way. Optimized for query on large dataset. In this case,
         * you should also override the `quarkus.langchain4j.pgvector.metadata.column-definitions` property to change the
         * type of the `metadata` column to COMBINED_JSONB.
         * </ul>
         * <p>
         * Default value: COMBINED_JSON
         */
        @WithDefault("COMBINED_JSON")
        MetadataStorageMode storageMode();

        /**
         * Metadata Definition: SQL definition of metadata field(s).
         * By default, "metadata JSON NULL" configured. This is only suitable if using the JSON metadata type.
         * <p>
         * If using JSONB metadata type, this should in most cases be set to `metadata JSONB NULL`.
         * <p>
         * If using COLUMNS metadata type, this should be a list of columns, one column for each desired metadata field.
         * Example: condominium_id uuid null, user uuid null
         */
        @WithDefault("metadata JSON NULL")
        List<String> columnDefinitions();

        /**
         * Metadata Indexes, list of fields to use as index.
         * <p>
         * For instance:
         * <ul>
         * <li>JSON: with JSON metadata, indexes are not allowed, so this property must be empty. To use indexes, switch to
         * JSONB metadata.
         * <li>JSONB: (metadata->'key'), (metadata->'name'), (metadata->'age')
         * <li>COLUMNS: key, name, age
         * </ul>
         */
        Optional<List<String>> indexes();

        /**
         * Index Type:
         * <ul>
         * <li>BTREE (default)
         * <li>GIN
         * <li>Other <a href="https://www.postgresql.org/docs/current/indexes-types.html">PostgreSQL index types</a>
         * </ul>
         */
        @WithDefault("BTREE")
        String indexType();
    }
}
