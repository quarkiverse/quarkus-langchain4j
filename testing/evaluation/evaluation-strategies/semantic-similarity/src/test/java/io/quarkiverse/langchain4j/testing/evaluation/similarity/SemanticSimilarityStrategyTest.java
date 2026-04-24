package io.quarkiverse.langchain4j.testing.evaluation.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;

class SemanticSimilarityStrategyTest {

    @Test
    void shouldPassWhenSimilarityIsAtLeastThreshold() {
        EmbeddingModel model = Mockito.mock(EmbeddingModel.class);
        when(model.embed("expected")).thenReturn(Response.from(Embedding.from(new float[] { 1f, 0f })));
        when(model.embed("actual")).thenReturn(Response.from(Embedding.from(new float[] { 0.95f, 0.05f })));

        SemanticSimilarityStrategy strategy = new SemanticSimilarityStrategy(model, 0.9);
        EvaluationSample<String> sample = new EvaluationSample<>("sample-1", Parameters.of("input"), "expected",
                java.util.List.of());

        EvaluationResult result = strategy.evaluate(sample, "actual");

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.explanation()).isNull();
        assertThat(result.metadata()).containsEntry("threshold", 0.9);
        assertThat(result.metadata()).containsKey("similarity");
    }

    @Test
    void shouldFailWhenSimilarityIsBelowThreshold() {
        EmbeddingModel model = Mockito.mock(EmbeddingModel.class);
        when(model.embed("expected")).thenReturn(Response.from(Embedding.from(new float[] { 1f, 0f })));
        when(model.embed("actual")).thenReturn(Response.from(Embedding.from(new float[] { 0f, 1f })));

        SemanticSimilarityStrategy strategy = new SemanticSimilarityStrategy(model, 0.9);
        EvaluationSample<String> sample = new EvaluationSample<>("sample-1", Parameters.of("input"), "expected",
                java.util.List.of());

        EvaluationResult result = strategy.evaluate(sample, "actual");

        assertThat(result.passed()).isFalse();
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.explanation())
                .matches("Similarity 0[\\.,]0000 below threshold 0[\\.,]9000");
        assertThat(result.metadata()).isEqualTo(Map.of("similarity", 0.0, "threshold", 0.9));
    }

    @Test
    void shouldCalculateCosineSimilarityForParallelVectors() {
        double similarity = SemanticSimilarityStrategy.calculateCosineSimilarity(
                new float[] { 1f, 2f, 3f },
                new float[] { 2f, 4f, 6f });

        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    void shouldRejectVectorsWithDifferentLengths() {
        assertThatThrownBy(() -> SemanticSimilarityStrategy.calculateCosineSimilarity(
                new float[] { 1f, 2f },
                new float[] { 1f }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vectors must be of the same length");
    }

    @Test
    void shouldRejectZeroMagnitudeVectors() {
        assertThatThrownBy(() -> SemanticSimilarityStrategy.calculateCosineSimilarity(
                new float[] { 0f, 0f },
                new float[] { 1f, 2f }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vector magnitude cannot be zero");
    }
}
