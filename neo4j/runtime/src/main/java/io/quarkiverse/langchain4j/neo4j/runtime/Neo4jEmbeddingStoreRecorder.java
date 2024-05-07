package io.quarkiverse.langchain4j.neo4j.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

import dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class Neo4jEmbeddingStoreRecorder {

    public Function<SyntheticCreationalContext<Neo4jEmbeddingStore>, Neo4jEmbeddingStore> embeddingStoreFunction(
            Neo4jRuntimeConfig config) {
        return new Function<>() {
            @Override
            public Neo4jEmbeddingStore apply(SyntheticCreationalContext<Neo4jEmbeddingStore> context) {
                Neo4jEmbeddingStore.Neo4jEmbeddingStoreBuilder builder = Neo4jEmbeddingStore.builder();
                Driver driver = context.getInjectedReference(Driver.class, new Default.Literal());
                builder.driver(driver);
                builder.dimension(config.dimension());
                builder.label(config.label());
                builder.embeddingProperty(config.embeddingProperty());
                builder.idProperty(config.idProperty());
                builder.metadataPrefix(config.metadataPrefix().orElse(""));
                builder.textProperty(config.textProperty());
                builder.indexName(config.indexName());
                builder.databaseName(config.databaseName());
                builder.retrievalQuery(config.retrievalQuery());
                builder.config(SessionConfig.forDatabase(config.databaseName()));
                return builder.build();
            }
        };
    }
}
