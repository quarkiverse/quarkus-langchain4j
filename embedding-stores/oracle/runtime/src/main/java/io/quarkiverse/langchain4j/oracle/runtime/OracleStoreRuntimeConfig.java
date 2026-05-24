package io.quarkiverse.langchain4j.oracle.runtime;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface OracleStoreRuntimeConfig {

    /**
     * The table name for storing embeddings.
     */
    @WithDefault("embeddings")
    String table();

    /**
     * Whether to create the embedding table if it does not already exist, replace it, or do nothing.
     * <ul>
     * <li>{@code CREATE_NONE}: the table must already exist</li>
     * <li>{@code CREATE_IF_NOT_EXISTS}: create the table if it does not exist</li>
     * <li>{@code CREATE_OR_REPLACE}: drop and recreate the table</li>
     * </ul>
     */
    @WithDefault("CREATE_IF_NOT_EXISTS")
    CreateOption createOption();

    /**
     * Custom name for the id column. Defaults to {@code id}.
     */
    Optional<String> idColumn();

    /**
     * Custom name for the embedding column. Defaults to {@code embedding}.
     */
    Optional<String> embeddingColumn();

    /**
     * Custom name for the text column. Defaults to {@code text}.
     */
    Optional<String> textColumn();

    /**
     * Custom name for the metadata column. Defaults to {@code metadata}.
     */
    Optional<String> metadataColumn();

    /**
     * Whether to use exact search (brute force) instead of approximate nearest neighbor search.
     */
    @WithDefault("false")
    boolean exactSearch();

    /**
     * Configuration for the IVF vector index used for approximate nearest neighbor search.
     * The index is only created when {@code vector-index.create-option} is not {@code CREATE_NONE}.
     */
    @ConfigDocSection
    @WithDefault("vector-index")
    VectorIndexConfig vectorIndex();

    /**
     * JSON metadata index configurations.
     * Each named entry defines a database index on one or more JSON metadata keys,
     * enabling faster filtering on metadata fields during search.
     */
    @ConfigDocSection
    @ConfigDocMapKey("index-name")
    @WithDefault("metadata-indexes")
    List<MetadataIndexConfig> metadataIndexes();

    @ConfigGroup
    interface VectorIndexConfig {

        /**
         * Whether to create the IVF vector index.
         * <ul>
         * <li>{@code CREATE_NONE}: do not create an index</li>
         * <li>{@code CREATE_IF_NOT_EXISTS}: create the index if it does not exist</li>
         * <li>{@code CREATE_OR_REPLACE}: drop and recreate the index</li>
         * </ul>
         */
        @WithDefault("CREATE_NONE")
        CreateOption createOption();

        /**
         * The target accuracy percentage (0-100) for the IVF vector index.
         * Higher values improve recall at the cost of search latency.
         */
        @WithDefault("-1")
        int targetAccuracy();

        /**
         * The degree of parallelism for IVF vector index creation.
         * Higher values speed up index creation on multi-core systems.
         */
        @WithDefault("-1")
        int degreeOfParallelism();

        /**
         * The number of neighbor partitions in the IVF index.
         * This controls how the vector space is divided during index creation.
         */
        @WithDefault("-1")
        int neighborPartitions();

        /**
         * The number of samples per partition used when building the IVF index.
         */
        @WithDefault("-1")
        int samplePerPartition();

        /**
         * The minimum number of vectors per partition in the IVF index.
         */
        @WithDefault("-1")
        int minVectorsPerPartition();
    }

    @ConfigGroup
    interface MetadataIndexConfig {

        /**
         * Whether this is a unique index.
         */
        @WithDefault("false")
        boolean unique();

        /**
         * Whether to create a bitmap index instead of a B-tree index.
         * Bitmap indexes are more efficient for low-cardinality columns.
         */
        @WithDefault("false")
        boolean bitmap();

        /**
         * Whether to create the metadata index.
         * <ul>
         * <li>{@code CREATE_NONE}: do not create the index</li>
         * <li>{@code CREATE_IF_NOT_EXISTS}: create the index if it does not exist</li>
         * <li>{@code CREATE_OR_REPLACE}: drop and recreate the index</li>
         * </ul>
         */
        @WithDefault("CREATE_IF_NOT_EXISTS")
        CreateOption createOption();

        /**
         * The metadata keys to include in this index.
         * Each entry defines a JSON metadata key, its SQL type, and sort order.
         */
        List<MetadataIndexKeyConfig> keys();
    }

    @ConfigGroup
    interface MetadataIndexKeyConfig {

        /**
         * The JSON metadata key name to index.
         */
        String key();

        /**
         * The SQL type of the indexed metadata key.
         * Allowed values: {@code STRING}, {@code INTEGER}, {@code LONG}, {@code FLOAT}, {@code DOUBLE}.
         */
        @WithDefault("STRING")
        String type();

        /**
         * The sort order for this key in the index.
         * Allowed values: {@code ASC}, {@code DESC}.
         */
        @WithDefault("ASC")
        String order();
    }
}
