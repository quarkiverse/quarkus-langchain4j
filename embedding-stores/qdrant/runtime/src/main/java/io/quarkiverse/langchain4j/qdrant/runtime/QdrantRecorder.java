package io.quarkiverse.langchain4j.qdrant.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.quarkiverse.qdrant.runtime.QdrantClient;
import io.quarkiverse.qdrant.runtime.QdrantClientName;
import io.quarkiverse.qdrant.runtime.QdrantConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QdrantRecorder {

    public Function<SyntheticCreationalContext<QdrantEmbeddingStore>, QdrantEmbeddingStore> qdrantStoreFunction(
            String clientName, String collectionName, String payloadTextKey) {
        return new Function<>() {
            @Override
            public QdrantEmbeddingStore apply(SyntheticCreationalContext<QdrantEmbeddingStore> context) {
                QdrantClient client;
                if (clientName == null || QdrantConfig.isDefaultClient(clientName)) {
                    client = context.getInjectedReference(QdrantClient.class, new Default.Literal());
                } else {
                    client = context.getInjectedReference(QdrantClient.class, QdrantClientName.Literal.of(clientName));
                }
                return new QdrantEmbeddingStore(client, collectionName, payloadTextKey);
            }
        };
    }
}
