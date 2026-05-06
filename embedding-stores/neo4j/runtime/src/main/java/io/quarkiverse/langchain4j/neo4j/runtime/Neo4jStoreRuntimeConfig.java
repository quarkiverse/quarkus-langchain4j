package io.quarkiverse.langchain4j.neo4j.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface Neo4jStoreRuntimeConfig {

    /**
     * Dimension of the embeddings that will be stored in the Neo4j store.
     */
    Optional<Integer> dimension();

    /**
     * Label for the created nodes.
     */
    @WithDefault("Document")
    String label();

    /**
     * Name of the property to store the embedding vectors.
     */
    @WithDefault("embedding")
    String embeddingProperty();

    /**
     * Name of the property to store embedding IDs.
     */
    @WithDefault("id")
    String idProperty();

    /**
     * Prefix to be added to the metadata keys. By default, no prefix is used.
     */
    Optional<String> metadataPrefix();

    /**
     * Name of the property to store the embedding text.
     */
    @WithDefault("text")
    String textProperty();

    /**
     * Name of the index to be created for vector search.
     */
    @WithDefault("vector")
    String indexName();

    /**
     * Name of the database to connect to.
     * <p>
     * Connecting to a database other than the default {@code neo4j} requires Neo4j Enterprise Edition.
     * On Community Edition only the default {@code neo4j} database exists, so this property must be left
     * unchanged (or unset) and named stores must be isolated via {@code label}, {@code index-name} and
     * {@code embedding-property} instead.
     */
    @WithDefault("neo4j")
    String databaseName();

    /**
     * The query to use when retrieving embeddings. This query has to return the following columns:
     * <ul>
     * <li>metadata</li>
     * <li>score</li>
     * <li>column of the same name as the 'id-property' value</li>
     * <li>column of the same name as the 'text-property' value</li>
     * <li>column of the same name as the 'embedding-property' value</li>
     * </ul>
     * <p>
     * If not set, a default query is automatically derived from the configured
     * {@code id-property}, {@code text-property}, and {@code embedding-property} values
     * for this store.
     */
    Optional<String> retrievalQuery();
}
