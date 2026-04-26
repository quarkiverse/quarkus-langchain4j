package io.quarkiverse.langchain4j.testing.evaluation;

/**
 * A strategy to evaluate the output of a model.
 * <p>
 * Strategies return {@link EvaluationResult} objects containing not just
 * pass/fail status, but also scores, explanations, and metadata.
 * </p>
 *
 * <h2>Example Implementation</h2>
 *
 * <pre>{@code
 * public class MyStrategy implements EvaluationStrategy<String> {
 *     @Override
 *     public EvaluationResult evaluate(EvaluationSample<String> sample, String output) {
 *         double score = calculateScore(sample.expectedOutput(), output);
 *         boolean passed = score >= 0.8;
 *
 *         if (passed) {
 *             return EvaluationResult.passed(score);
 *         } else {
 *             return EvaluationResult.failed(
 *                     score,
 *                     String.format("Score %.2f below threshold 0.80", score));
 *         }
 *     }
 * }
 * }</pre>
 *
 * @param <T> the type of the output.
 */
public interface EvaluationStrategy<T> {

    /**
     * Evaluate the output of a model.
     * <p>
     * Returns an evaluation result containing the pass/fail status,
     * a numeric score (0.0 to 1.0), an optional explanation, and optional
     * strategy-specific metadata.
     * </p>
     *
     * @param sample the sample to evaluate.
     * @param output the output of the model.
     * @return the evaluation result
     */
    EvaluationResult evaluate(EvaluationSample<T> sample, T output);

}
