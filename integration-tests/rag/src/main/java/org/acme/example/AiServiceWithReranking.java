package org.acme.example;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Singleton;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@RagPipeline(augmentor = AiServiceWithReranking.RerankingAugmentor.class)
public interface AiServiceWithReranking {

    String chat(String message);

    @Singleton
    class RerankingAugmentor implements RetrievalAugmentor {

        private final RetrievalAugmentor delegate;

        RerankingAugmentor() {
            ContentRetriever retriever = new ContentRetriever() {
                @Override
                public List<Content> retrieve(Query query) {
                    if (query.text().equals("What is the fastest car?")) {
                        return List.of(Content.from("Ferrari goes 350"),
                                Content.from("Bugatti goes 450"));
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

            delegate = DefaultRetrievalAugmentor.builder()
                    .contentRetriever(retriever)
                    .contentAggregator(new ReRankingContentAggregator(scoringModel,
                            ReRankingContentAggregator.DEFAULT_QUERY_SELECTOR, 0.8))
                    .build();
        }

        @Override
        public AugmentationResult augment(AugmentationRequest request) {
            return delegate.augment(request);
        }
    }
}
