package org.acme.example;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Singleton;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = AiServiceWithQueryTransformer.QueryCompressionAugmentor.class)
public interface AiServiceWithQueryTransformer {

    String chat(String message);

    /**
     * Contains a query transformer that transforms the query text to
     * lowercase. If the transformed worked properly, the content retriever
     * will append content saying "The transformer works!".
     */
    @Singleton
    class QueryCompressionAugmentor implements Supplier<RetrievalAugmentor> {

        @Override
        public RetrievalAugmentor get() {
            return DefaultRetrievalAugmentor.builder()
                    .queryTransformer(query -> Collections.singleton(new Query(query.text().toLowerCase())))
                    .contentRetriever(new ContentRetriever() {
                        @Override
                        public List<Content> retrieve(Query query) {
                            if (query.text().equals("hello")) {
                                return Collections.singletonList(Content.from("The transformer works!"));
                            } else {
                                return Collections.emptyList();
                            }
                        }
                    })
                    .build();
        }
    }
}
