package io.quarkiverse.langchain4j.samples;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;

public class ContextualRagExample {

    // tag::createQueryTransformer[]
    public QueryTransformer createQueryTransformer(ChatModel chatModel) {
        return question -> {
            String compressed = chatModel.chat("Summarize this: " + question.text());
            return List.of(Query.from(compressed));
        };
    }
    // end::createQueryTransformer[]

    // tag::getDefaultQueryTransformer[]
    public QueryTransformer getDefaultQueryTransformer(ChatModel chatModel) {
        return new CompressingQueryTransformer(chatModel);
    }
    // end::getDefaultQueryTransformer[]

    // tag::createContentRetrievers[]
    public List<ContentRetriever> createContentRetrievers(EmbeddingModel model,
            EmbeddingStore<TextSegment> store,
            WebSearchEngine webSearchEngine) {
        ContentRetriever vectorRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(model)
                .embeddingStore(store)
                .maxResults(3)
                .build();

        ContentRetriever webRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .build();

        return List.of(vectorRetriever, webRetriever);
    }
    // end::createContentRetrievers[]

    // tag::defaultRouter[]
    public QueryRouter createDefaultRouter(
            List<ContentRetriever> contentRetrievers) {
        // Return all content retrievers
        return new DefaultQueryRouter(contentRetrievers);
    }
    // end::defaultRouter[]

    // tag::createQueryRouter[]
    public QueryRouter createQueryRouter(ChatModel chatModel,
            ContentRetriever vectorSearchRetriever,
            ContentRetriever webSearchRetriever) {
        // Create a query router that uses a chat model to route queries
        return LanguageModelQueryRouter.builder()
                .chatModel(chatModel)
                .retrieverToDescription(Map.of(
                        vectorSearchRetriever, "Retrieve relevant documents about terms and conditions",
                        webSearchRetriever, "Retrieve relevant documents from the quarkus.io website"))
                .build();
    }
    // end::createQueryRouter[]

    // tag::createQueryRouterWithFallback[]
    public QueryRouter createQueryRouterWithFallback(ChatModel chatModel,
            ContentRetriever vectorSearchRetriever,
            ContentRetriever webSearchRetriever) {
        // Create a query router that uses a chat model to route queries
        return LanguageModelQueryRouter.builder()
                .chatModel(chatModel)
                .fallbackStrategy(LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL)
                .retrieverToDescription(Map.of(
                        vectorSearchRetriever, "Retrieve relevant documents about terms and conditions",
                        webSearchRetriever, "Retrieve relevant documents from the quarkus.io website"))
                .build();
    }
    // end::createQueryRouterWithFallback[]

    // tag::createContentInjector[]
    public ContentInjector createContentInjector() {
        return (contents, userMessage) -> {
            StringBuilder prompt = new StringBuilder(userMessage.toString() + "\n\nRelevant facts:\n");
            for (Content content : contents) {
                prompt.append("- ").append(content.textSegment().text()).append("\n");
            }
            return UserMessage.userMessage(prompt.toString());
        };
    }
    // end::createContentInjector[]

    ContentRetriever getVectorSearchRetriever() {
        return null;
    }

    ContentRetriever getWebSearchRetriever() {
        return null;
    }

    // tag::retriever[]
    @Produces
    @ApplicationScoped
    public RetrievalAugmentor createRetrievalAugmentor(
            ChatModel chatModel, // Default chat model, you can also use @ModelName to select it
            ScoringModel scoringModel) {
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(getDefaultQueryTransformer(chatModel))
                .queryRouter(createQueryRouter(chatModel, getVectorSearchRetriever(), getWebSearchRetriever()))
                .contentAggregator(createAggregator(scoringModel))
                .contentInjector(createContentInjector())
                .build();
    }
    // end::retriever[]

    // tag::createAggregator[]
    public ContentAggregator createAggregator(ScoringModel model) {
        return ReRankingContentAggregator.builder()
                .scoringModel(model)
                .minScore(0.6)
                .build();
    }
    // end::createAggregator[]
}
