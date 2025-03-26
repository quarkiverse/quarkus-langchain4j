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

/**
 * IMPORTANT: Note that this retrieval augmentor isn't used by the sample out of the box.
 * It shows an alternative approach to using a web search engine - by default,
 * the bot uses the tools from dev.langchain4j.web.search.WebSearchTool.
 * If you want to switch it to this retrieval augmentor, modify the Bot interface and
 * add a "retrievalAugmentor = WebSearchRetrievalAugmentor.class" parameter to
 * the @RegisterAiService annotation. However, this will prevent the bot from using
 * the AdditionalTools.getTodaysDate() method before calling Tavily.
 */
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
                    String query = chatModel.chat("Transform the user's question into a suitable query for the " +
                            "Tavily search engine. The query should yield the results relevant to answering the user's question." +
                            "User's question: " + question.text());
                    return Collections.singleton(Query.from(query));
                }).contentRetriever(new WebSearchContentRetriever(webSearchEngine, 10))
                .build();
    }
}
