package io.quarkiverse.langchain4j.memorystore.mongodb.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import com.mongodb.client.MongoClient;

import io.quarkiverse.langchain4j.memorystore.MongoDBChatMemoryStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.mongodb.MongoClientName;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MongoDBMemoryStoreRecorder {
    public Function<SyntheticCreationalContext<MongoDBChatMemoryStore>, MongoDBChatMemoryStore> chatMemoryStoreFunction(
            String clientName, String database, String collection) {
        return new Function<>() {
            @Override
            public MongoDBChatMemoryStore apply(SyntheticCreationalContext<MongoDBChatMemoryStore> context) {
                MongoClient mongoClient;
                if (clientName == null) {
                    mongoClient = context.getInjectedReference(MongoClient.class, new Default.Literal());
                } else {
                    mongoClient = context.getInjectedReference(MongoClient.class,
                            MongoClientName.Literal.of(clientName));
                }
                return new MongoDBChatMemoryStore(mongoClient, database, collection);
            }
        };
    }
}
