package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.function.Supplier;

@ApplicationScoped
public class WebSearchRetrievalAugmentor implements Supplier<RetrievalAugmentor> {

    @Inject
    WebSearchEngine webSearchEngine;

    @Inject
    ChatLanguageModel chatModel;

    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer((question) -> {
                    String query = chatModel.generate("Transform the user's question into a suitable query for the " +
                            "Tavily search engine. The query should yield the results relevant to answering the user's question." +
                            "User's question: " + question.text());
                    return Collections.singleton(Query.from(query));
                }).contentRetriever(new WebSearchContentRetriever(webSearchEngine, 10))
                .build();
    }
}
