package io.quarkiverse.langchain4j.testing.scorer.similarity;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.testing.scorer.EvaluationSample;
import io.quarkiverse.langchain4j.testing.scorer.EvaluationStrategy;

/**
 * A strategy to evaluate the output of a model using semantic similarity.
 */
public class SemanticSimilarityStrategy implements EvaluationStrategy<String> {

    private final EmbeddingModel model;
    private final double minSimilarity;

    /**
     * Create a new instance of `SemanticSimilarityStrategy`.
     *
     * @param model the embedding model to use to calculate the similarity.
     * @param minSimilarity the minimum similarity required to consider the output correct.
     */
    public SemanticSimilarityStrategy(EmbeddingModel model, double minSimilarity) {
        this.model = model;
        this.minSimilarity = minSimilarity;
    }

    /**
     * Create a new instance of `SemanticSimilarityStrategy` using the default model (`BgeSmallEnV15`) and a default minimum
     * similarity.
     */
    public SemanticSimilarityStrategy() {
        this(new BgeSmallEnV15EmbeddingModel(), 0.9);
    }

    /**
     * Create a new instance of `SemanticSimilarityStrategy` using the default model and a custom minimum similarity.
     *
     * @param minSimilarity the minimum similarity required to consider the output correct.
     */
    public SemanticSimilarityStrategy(double minSimilarity) {
        this(new BgeSmallEnV15EmbeddingModel(), minSimilarity);
    }

    /**
     * Evaluate the output of a model.
     *
     * @param sample the sample to evaluate.
     * @param output the output of the model.
     * @return {@code true} if the output is correct, {@code false} otherwise.
     */
    @Override
    public boolean evaluate(EvaluationSample<String> sample, String output) {
        Response<Embedding> actual = model.embed(output);
        Response<Embedding> expectation = model.embed(sample.expectedOutput());
        return calculateCosineSimilarity(expectation.content().vector(),
                actual.content().vector()) > minSimilarity;
    }

    public static double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must be of the same length");
        }

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        // Calculate dot product and magnitudes
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            magnitudeA += Math.pow(vectorA[i], 2);
            magnitudeB += Math.pow(vectorB[i], 2);
        }

        // Compute magnitudes
        magnitudeA = Math.sqrt(magnitudeA);
        magnitudeB = Math.sqrt(magnitudeB);

        // Avoid division by zero
        if (magnitudeA == 0 || magnitudeB == 0) {
            throw new IllegalArgumentException("Vector magnitude cannot be zero");
        }

        return dotProduct / (magnitudeA * magnitudeB);
    }
}
