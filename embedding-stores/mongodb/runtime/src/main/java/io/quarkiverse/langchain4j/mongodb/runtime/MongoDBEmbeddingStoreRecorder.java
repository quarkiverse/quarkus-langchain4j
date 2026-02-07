package io.quarkiverse.langchain4j.mongodb.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import com.mongodb.client.MongoClient;

import io.quarkiverse.langchain4j.mongodb.MongoDBEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.mongodb.MongoClientName;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MongoDBEmbeddingStoreRecorder {

    private final RuntimeValue<MongoDBEmbeddingStoreConfig> runtimeConfig;

    public MongoDBEmbeddingStoreRecorder(RuntimeValue<MongoDBEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<MongoDBEmbeddingStore>, MongoDBEmbeddingStore> embeddingStoreFunction(
            String clientName) {
        return new Function<>() {
            @Override
            public MongoDBEmbeddingStore apply(SyntheticCreationalContext<MongoDBEmbeddingStore> context) {
                MongoClient mongoClient;
                if (clientName == null) {
                    mongoClient = context.getInjectedReference(MongoClient.class, Default.Literal.INSTANCE);
                } else {
                    mongoClient = context.getInjectedReference(MongoClient.class, new MongoClientName.Literal(clientName));
                }

                MongoDBEmbeddingStoreConfig config = runtimeConfig.getValue();
                return new MongoDBEmbeddingStore(
                        mongoClient,
                        config.databaseName(),
                        config.collectionName(),
                        config.indexName(),
                        config.vectorFieldName(),
                        config.textFieldName(),
                        config.metadataFieldName());
            }
        };
    }
}
