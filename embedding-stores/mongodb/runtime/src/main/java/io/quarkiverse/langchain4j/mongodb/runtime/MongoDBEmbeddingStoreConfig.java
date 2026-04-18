package io.quarkiverse.langchain4j.mongodb.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the MongoDB embedding store.
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.mongodb")
public interface MongoDBEmbeddingStoreConfig {

    /**
     * The database to use.
     */
    @WithDefault("default")
    String databaseName();

    /**
     * The collection to use.
     */
    @WithDefault("embeddings")
    String collectionName();

    /**
     * The name of the vector index to use.
     */
    @WithDefault("vector_index")
    String indexName();

    /**
     * The name of the field to store the vector.
     */
    @WithDefault("embedding")
    String vectorFieldName();

    /**
     * The name of the field to store the text.
     */
    @WithDefault("text")
    String textFieldName();

    /**
     * The number of dimensions of the embedding vector. Default is 768, with a max of 8192.
     */
    @WithDefault("768")
    int dimensions();

    /**
     * The similarity search algorithm to use. Can be "cosine", "dotProduct" or "euclidean".
     */
    @WithDefault("cosine")
    SimilaritySearch similaritySearch();

    /**
     * The name of the field that will store the similarity score returned by the vector search.
     * This field is used to rank results by relevance (e.g., higher scores indicate better matches).
     * The score is typically a floating-point value between 0 and 1 (or negative for some algorithms),
     * depending on the chosen similarity search method.
     */
    @WithDefault("score")
    String scoreFieldName();

    /**
     * The name of the field to store the metadata.
     */
    @WithDefault("metadata")
    String metadataFieldName();
}
