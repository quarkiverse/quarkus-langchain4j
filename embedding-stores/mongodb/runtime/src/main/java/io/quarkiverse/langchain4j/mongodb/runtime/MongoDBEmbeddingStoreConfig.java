package io.quarkiverse.langchain4j.mongodb.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Optional;

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
     * The name of the field to store the metadata.
     */
    @WithDefault("metadata")
    String metadataFieldName();
}
