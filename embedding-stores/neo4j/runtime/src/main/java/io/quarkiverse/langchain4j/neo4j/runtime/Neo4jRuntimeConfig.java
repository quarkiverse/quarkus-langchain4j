package io.quarkiverse.langchain4j.neo4j.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.neo4j")
public interface Neo4jRuntimeConfig {

    /**
     * Dimension of the embeddings that will be stored in the Neo4j store.
     */
    Integer dimension();

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
     */
    @WithDefault("""
            RETURN properties(node) AS metadata, \
            node.${quarkus.langchain4j.neo4j.id-property} AS ${quarkus.langchain4j.neo4j.id-property}, \
            node.${quarkus.langchain4j.neo4j.text-property} AS ${quarkus.langchain4j.neo4j.text-property}, \
            node.${quarkus.langchain4j.neo4j.embedding-property} AS ${quarkus.langchain4j.neo4j.embedding-property}, \
            score""")
    String retrievalQuery();

}
