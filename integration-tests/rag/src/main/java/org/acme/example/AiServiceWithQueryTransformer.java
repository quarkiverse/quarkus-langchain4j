package org.acme.example;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Singleton;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@RagPipeline(retrievers = {
        AiServiceWithQueryTransformer.LowercaseRetriever.class }, transformer = AiServiceWithQueryTransformer.LowercaseTransformer.class)
public interface AiServiceWithQueryTransformer {

    String chat(String message);

    @Singleton
    class LowercaseTransformer implements QueryTransformer {
        @Override
        public Collection<Query> transform(Query query) {
            return Collections.singleton(new Query(query.text().toLowerCase()));
        }
    }

    @Singleton
    class LowercaseRetriever implements ContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            if (query.text().equals("hello")) {
                return Collections.singletonList(Content.from("The transformer works!"));
            } else {
                return Collections.emptyList();
            }
        }
    }
}
