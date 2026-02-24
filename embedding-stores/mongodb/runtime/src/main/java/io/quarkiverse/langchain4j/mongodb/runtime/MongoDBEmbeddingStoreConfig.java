package io.quarkiverse.langchain4j.mongodb.runtime;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

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

    @WithDefault("score")
    String scoreFieldName();

    /**
     * The name of the field to store the metadata.
     */
    @WithDefault("metadata")
    String metadataFieldName();
}
