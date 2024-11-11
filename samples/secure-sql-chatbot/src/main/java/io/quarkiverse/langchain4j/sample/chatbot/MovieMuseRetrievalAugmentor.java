package io.quarkiverse.langchain4j.sample.chatbot;

import java.util.function.Supplier;

import org.eclipse.microprofile.context.ManagedExecutor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;

@ApplicationScoped
public class MovieMuseRetrievalAugmentor implements Supplier<RetrievalAugmentor> {

    @Inject
    ChatLanguageModel model;

    @Inject
    MovieDatabaseContentRetriever contentRetriever;

    @Inject
    ManagedExecutor executor;

    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                // The compressing transformer compresses the history of
                // chat messages to add the necessary context
                // to the current query. For example, if the user asks
                // "What is the length of Inception?" and then the next query is
                // "When was it released?", then the transformer will compress
                // the second query to something like "Release year of Inception",
                // which is then used by the content retriever to generate
                // the necessary SQL query.
                .queryTransformer(new CompressingQueryTransformer(model))
                // The content retriever takes a user's question (including
                // the context provided by the transformer in the previous step), asks a LLM
                // to generate a SQL query from it, then executes the query and
                // provides the resulting data back to the LLM, so it can
                // generate a proper text response from it.
                .contentRetriever(contentRetriever).executor(executor).build();
    }
}
