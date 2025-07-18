package io.quarkiverse.langchain4j.neo4j.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class Neo4jEmbeddingStoreRecorder {
    private final RuntimeValue<Neo4jRuntimeConfig> runtimeConfig;

    public Neo4jEmbeddingStoreRecorder(RuntimeValue<Neo4jRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<Neo4jEmbeddingStore>, Neo4jEmbeddingStore> embeddingStoreFunction() {
        return new Function<>() {
            @Override
            public Neo4jEmbeddingStore apply(SyntheticCreationalContext<Neo4jEmbeddingStore> context) {
                var builder = Neo4jEmbeddingStore.builder();
                Driver driver = context.getInjectedReference(Driver.class, new Default.Literal());
                builder.driver(driver);
                builder.dimension(runtimeConfig.getValue().dimension());
                builder.label(runtimeConfig.getValue().label());
                builder.embeddingProperty(runtimeConfig.getValue().embeddingProperty());
                builder.idProperty(runtimeConfig.getValue().idProperty());
                builder.metadataPrefix(runtimeConfig.getValue().metadataPrefix().orElse(""));
                builder.textProperty(runtimeConfig.getValue().textProperty());
                builder.indexName(runtimeConfig.getValue().indexName());
                builder.databaseName(runtimeConfig.getValue().databaseName());
                builder.retrievalQuery(runtimeConfig.getValue().retrievalQuery());
                builder.config(SessionConfig.forDatabase(runtimeConfig.getValue().databaseName()));
                return builder.build();
            }
        };
    }
}
