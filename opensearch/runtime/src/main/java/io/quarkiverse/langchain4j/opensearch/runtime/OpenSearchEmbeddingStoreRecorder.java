package io.quarkiverse.langchain4j.opensearch.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import org.opensearch.client.opensearch.OpenSearchClient;

import io.quarkiverse.langchain4j.opensearch.OpenSearchEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OpenSearchEmbeddingStoreRecorder {

    public Function<SyntheticCreationalContext<OpenSearchEmbeddingStore>, OpenSearchEmbeddingStore> embeddingStoreFunction(
            OpenSearchEmbeddingStoreConfig config) {
        return new Function<>() {
            @Override
            public OpenSearchEmbeddingStore apply(SyntheticCreationalContext<OpenSearchEmbeddingStore> context) {
                OpenSearchEmbeddingStore.Builder builder = new OpenSearchEmbeddingStore.Builder();
                OpenSearchClient openSearchClient;
                openSearchClient = context.getInjectedReference(OpenSearchClient.class, new Default.Literal());
                builder.openSearchClient(openSearchClient);
                return builder.build();
            }
        };
    }
}
