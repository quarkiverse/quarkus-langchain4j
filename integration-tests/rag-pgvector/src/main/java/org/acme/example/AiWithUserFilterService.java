package org.acme.example;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiWithUserFilterService {

    String chat(@MemoryId String userId, @UserMessage String message);

    @ApplicationScoped
    @SuppressWarnings("unused")
    class AugmentorProducer {

        /**
         * The embedding store (the database).
         * The bean is provided by the quarkus-langchain4j-pgvector extension.
         */
        @Inject
        EmbeddingStore<TextSegment> store;

        @Inject
        EmbeddingModel embeddingModel;

        @Produces
        public RetrievalAugmentor get() {
            Function<Query, Filter> filterByUserId = (query) -> metadataKey("userId")
                    .isEqualTo(query.metadata().chatMemoryId().toString());

            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(store)
                    // by specifying the dynamic filter, we limit the search to segments that belong only to the current user
                    .dynamicFilter(filterByUserId)
                    .maxResults(10)
                    .build();

            return DefaultRetrievalAugmentor.builder()
                    .contentRetriever(contentRetriever)
                    .build();
        }
    }
}
