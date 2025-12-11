package io.quarkiverse.langchain4j.testing.evaluation.similarity;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * A strategy to evaluate the output of a model using semantic similarity.
 */
public class SemanticSimilarityStrategy implements EvaluationStrategy<String> {

    private final EmbeddingModel model;
    private final double minSimilarity;

    private static final boolean BGE_SMALL_EN_V_15_EMBEDDING_MODEL_EXISTS;

    static {
        boolean bgeSmallEnV15EmbeddingModelExists;
        try {
            Class.forName("dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel", false,
                    Thread.currentThread()
                            .getContextClassLoader());
            bgeSmallEnV15EmbeddingModelExists = true;
        } catch (ClassNotFoundException e) {
            bgeSmallEnV15EmbeddingModelExists = false;
        }
        BGE_SMALL_EN_V_15_EMBEDDING_MODEL_EXISTS = bgeSmallEnV15EmbeddingModelExists;
    }

    /**
     * Create a new instance of `SemanticSimilarityStrategy` using the default model (`BgeSmallEnV15`) and a default minimum
     * similarity.
     */
    public SemanticSimilarityStrategy() {
        this(0.9);
    }

    /**
     * Create a new instance of `SemanticSimilarityStrategy` using the default model and a custom minimum similarity.
     *
     * @param minSimilarity the minimum similarity required to consider the output correct.
     */
    public SemanticSimilarityStrategy(double minSimilarity) {
        this(loadBgeSmallEnV15EmbeddingModel(), minSimilarity);
    }

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

    private static EmbeddingModel loadBgeSmallEnV15EmbeddingModel() {
        if (!BGE_SMALL_EN_V_15_EMBEDDING_MODEL_EXISTS) {
            throw new IllegalStateException(
                    "BgSmallEnV15EmbeddingModel is not part of the classpath. Please add the 'dev.langchain4j:langchain4j-embeddings-bge-small-en-v15' dependency to the classpath");
        }
        return new BgeSmallEnV15EmbeddingModel();
    }

    /**
     * Evaluate the output of a model using semantic similarity.
     * <p>
     * Returns an evaluation result containing the actual similarity score,
     * pass/fail status based on the minimum similarity threshold, and an
     * explanation if the similarity is below the threshold.
     * </p>
     *
     * @param sample the sample to evaluate.
     * @param output the output of the model.
     * @return an evaluation result with similarity score and explanation
     */
    @Override
    public EvaluationResult evaluate(
            EvaluationSample<String> sample, String output) {
        Response<Embedding> actual = model.embed(output);
        Response<Embedding> expectation = model.embed(sample.expectedOutput());
        double similarity = calculateCosineSimilarity(
                expectation.content().vector(),
                actual.content().vector());

        boolean passed = similarity >= minSimilarity;

        if (passed) {
            return io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult.passed(similarity)
                    .withMetadata(java.util.Map.of(
                            "similarity", similarity,
                            "threshold", minSimilarity));
        } else {
            return io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult.failed(
                    similarity,
                    String.format("Similarity %.4f below threshold %.4f", similarity, minSimilarity))
                    .withMetadata(java.util.Map.of(
                            "similarity", similarity,
                            "threshold", minSimilarity));
        }
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
