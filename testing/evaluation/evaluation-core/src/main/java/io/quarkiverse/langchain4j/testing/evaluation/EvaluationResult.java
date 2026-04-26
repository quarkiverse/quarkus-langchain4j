package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.Map;

/**
 * Evaluation result containing not just pass/fail status, but also
 * scores, explanations, and strategy-specific metadata.
 * <p>
 * This record provides detailed information about an evaluation, including:
 * </p>
 * <ul>
 * <li><strong>passed</strong> - Whether the evaluation passed</li>
 * <li><strong>score</strong> - Numeric score from 0.0 to 1.0</li>
 * <li><strong>explanation</strong> - Optional human-readable explanation (e.g., why it failed)</li>
 * <li><strong>metadata</strong> - Strategy-specific additional information</li>
 * </ul>
 *
 */
public record EvaluationResult(
        boolean passed,
        double score,
        String explanation,
        Map<String, Object> metadata) {

    /**
     * Compact constructor with validation.
     */
    public EvaluationResult {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0, got: " + score);
        }
        // Make metadata immutable with defensive copy
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Create a passing evaluation result with the given score.
     *
     * @param score the evaluation score (0.0 to 1.0)
     * @return a passing evaluation result
     */
    public static EvaluationResult passed(double score) {
        return new EvaluationResult(true, score, null, Map.of());
    }

    /**
     * Create a failing evaluation result with the given score and explanation.
     *
     * @param score the evaluation score (0.0 to 1.0)
     * @param explanation human-readable explanation of why it failed
     * @return a failing evaluation result
     */
    public static EvaluationResult failed(double score, String explanation) {
        return new EvaluationResult(false, score, explanation, Map.of());
    }

    /**
     * Create a failing evaluation result with score 0.0.
     *
     * @param explanation human-readable explanation of why it failed
     * @return a failing evaluation result with score 0.0
     */
    public static EvaluationResult failed(String explanation) {
        return failed(0.0, explanation);
    }

    /**
     * Create an evaluation result from a boolean value.
     * <p>
     * Passing evaluations get score 1.0, failing get score 0.0.
     * </p>
     *
     * @param passed whether the evaluation passed
     * @return an evaluation result
     */
    public static EvaluationResult fromBoolean(boolean passed) {
        return new EvaluationResult(passed, passed ? 1.0 : 0.0, null, Map.of());
    }

    /**
     * Create a new evaluation result with additional metadata.
     *
     * @param additionalMetadata metadata to add
     * @return a new evaluation result with combined metadata
     */
    public EvaluationResult withMetadata(Map<String, Object> additionalMetadata) {
        Map<String, Object> combined = new java.util.HashMap<>(this.metadata);
        combined.putAll(additionalMetadata);
        return new EvaluationResult(this.passed, this.score, this.explanation, combined);
    }

    /**
     * Create a new evaluation result with an explanation.
     *
     * @param explanation the explanation to add
     * @return a new evaluation result with the explanation
     */
    public EvaluationResult withExplanation(String explanation) {
        return new EvaluationResult(this.passed, this.score, explanation, this.metadata);
    }
}
