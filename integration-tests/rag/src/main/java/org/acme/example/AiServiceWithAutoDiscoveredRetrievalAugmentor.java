package org.acme.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiServiceWithAutoDiscoveredRetrievalAugmentor {

    String chat(String message);

    @ApplicationScoped
    class AugmentorProducer {

        @Inject
        InMemoryEmbeddingStore<TextSegment> store;

        @Inject
        EmbeddingModel embeddingModel;

        @Produces
        public RetrievalAugmentor get() {
            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(store)
                    .maxResults(1)
                    .build();
            return DefaultRetrievalAugmentor.builder()
                    .contentRetriever(contentRetriever)
                    .build();
        }
    }
}
