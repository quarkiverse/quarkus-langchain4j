package org.acme.example;

import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@RagPipeline(retrievers = { AiServiceWithSpecifiedRetrievalAugmentor.NaiveRetriever.class })
public interface AiServiceWithSpecifiedRetrievalAugmentor {

    String chat(String message);

    @Singleton
    class NaiveRetriever implements ContentRetriever {
        @Inject
        InMemoryEmbeddingStore<TextSegment> store;

        @Inject
        EmbeddingModel embeddingModel;

        private EmbeddingStoreContentRetriever delegate;

        @PostConstruct
        void init() {
            delegate = EmbeddingStoreContentRetriever.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(store)
                    .maxResults(1)
                    .build();
        }

        @Override
        public List<Content> retrieve(Query query) {
            return delegate.retrieve(query);
        }
    }
}
