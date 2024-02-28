package org.acme.example;

import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = AiServiceWithSpecifiedRetrievalAugmentor.NaiveRagAugmentor.class)
public interface AiServiceWithSpecifiedRetrievalAugmentor {

    String chat(String message);

    @Singleton
    class NaiveRagAugmentor implements Supplier<RetrievalAugmentor> {

        @Inject
        InMemoryEmbeddingStore<TextSegment> store;

        @Inject
        EmbeddingModel embeddingModel;

        @Override
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
