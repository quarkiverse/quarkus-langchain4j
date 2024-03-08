package org.acme.example;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Singleton;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = AiServiceWithReranking.AugmentorWithReranking.class)
public interface AiServiceWithReranking {

    String chat(String message);

    @Singleton
    class AugmentorWithReranking implements Supplier<RetrievalAugmentor> {

        ContentRetriever retriever = new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                if (query.text().equals("What is the fastest car?")) {
                    return List.of(new Content("Ferrari goes 350"),
                            new Content("Bugatti goes 450"));
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        };

        ScoringModel scoringModel = new ScoringModel() {
            @Override
            public Response<List<Double>> scoreAll(List<TextSegment> documents, String query) {
                List<Double> scores = new ArrayList<>();
                for (TextSegment document : documents) {
                    if (document.text().equals("Ferrari goes 350")) {
                        scores.add(0.5);
                    } else if (document.text().equals("Bugatti goes 450")) {
                        scores.add(0.9);
                    } else {
                        scores.add(0.0);
                    }
                }
                return new Response<>(scores);
            }
        };

        @Override
        public RetrievalAugmentor get() {
            return DefaultRetrievalAugmentor.builder()
                    .contentRetriever(retriever)
                    .contentAggregator(new ReRankingContentAggregator(scoringModel,
                            ReRankingContentAggregator.DEFAULT_QUERY_SELECTOR,
                            0.8))
                    .build();
        }
    }
}
