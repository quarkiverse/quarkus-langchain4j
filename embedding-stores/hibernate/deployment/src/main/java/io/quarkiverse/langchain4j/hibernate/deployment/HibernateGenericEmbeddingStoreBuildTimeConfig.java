package io.quarkiverse.langchain4j.hibernate.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.generic")
public interface HibernateGenericEmbeddingStoreBuildTimeConfig {

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
     * Whether the vector index should be created if not already existing.
     */
    @WithDefault("false")
    boolean createIndex();

    /**
     *
     * A fine-tuning parameter for configuring the vector index, like e.g. {@code lists=1} for the pgvector IVFFlat index.
     * Consult the database vendor documentation for details about the possible index options.
     */
    Optional<String> indexOptions();

    /**
     * The database specific type of the vector index, like e.g. {@code hnsw} or {@code ivfflat} on pgvector.
     * Consult the database vendor documentation for details about the possible vector index types.
     */
    Optional<String> indexType();

    /**
     * Schema management configuration.
     */
    @ConfigDocSection
    SchemaManagement schemaManagement();

    /**
     * The distance function to use.
     */
    @WithDefault("COSINE")
    DistanceFunction distanceFunction();

    @ConfigGroup
    interface SchemaManagement {
        /**
         * Select whether the database schema is generated or not.
         * <p>
         * `drop-and-create` is awesome in development mode.
         * <p>
         * This defaults to 'none'.
         * <p>
         * However if Dev Services is in use and no other extensions that manage the schema are present
         * the value will be automatically overridden to 'drop-and-create'.
         * <p>
         * Accepted values: `none`, `create`, `drop-and-create`, `drop`, `update`, `validate`.
         *
         * @asciidoclet
         */
        @WithDefault("none")
        HibernateGenerationStrategy strategy();
    }

    enum HibernateGenerationStrategy {
        /**
         * No schema action.
         *
         * @asciidoclet
         */
        NONE,
        /**
         * Create the schema.
         *
         * @asciidoclet
         */
        CREATE,
        /**
         * Drop and then recreate the schema.
         *
         * @asciidoclet
         */
        DROP_AND_CREATE,
        /**
         * Drop the schema.
         *
         * @asciidoclet
         */
        DROP,
        /**
         * Update (alter) the database schema.
         *
         * @asciidoclet
         */
        UPDATE,
        /**
         * Validate the database schema.
         *
         * @asciidoclet
         */
        VALIDATE;

    }

}
