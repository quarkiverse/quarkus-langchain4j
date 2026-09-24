package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import com.mongodb.client.MongoClient;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.mongodb.MongoClientName;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MongoChatScopeStoreRecorder {
    public Function<SyntheticCreationalContext<MongoChatScopeStore>, MongoChatScopeStore> chatScopeStoreFunction(
            String clientName, String database, String collection) {
        return new Function<>() {
            @Override
            public MongoChatScopeStore apply(SyntheticCreationalContext<MongoChatScopeStore> context) {
                MongoClient mongoClient;
                if (clientName == null) {
                    mongoClient = context.getInjectedReference(MongoClient.class, new Default.Literal());
                } else {
                    mongoClient = context.getInjectedReference(MongoClient.class,
                            MongoClientName.Literal.of(clientName));
                }
                return new MongoChatScopeStore(mongoClient, database, collection);
            }
        };
    }
}
