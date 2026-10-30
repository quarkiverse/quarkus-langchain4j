package io.quarkiverse.langchain4j.testing.evaluation;

import static java.util.Objects.requireNonNull;

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

    /**
     * Whether this strategy is advisory.
     * <p>
     * An advisory strategy is run on every sample and its results are part of the report, but they do not count
     * toward the score, the confidence interval or the assertions. Use it for checks whose verdict is informative
     * but too noisy to fail a build on its own, typically an AI judge.
     * </p>
     *
     * @return {@code true} if the results of this strategy must not affect the score, {@code false} by default
     */
    default boolean isAdvisory() {
        return false;
    }

    /**
     * Wrap a strategy so that its results are reported but do not affect the score, see {@link #isAdvisory()}.
     *
     * @param strategy the strategy to wrap, must not be {@code null}
     * @param <T> the type of the output
     * @return an advisory strategy delegating to the given one, or the strategy itself if it is already advisory
     */
    static <T> EvaluationStrategy<T> toAdvisory(EvaluationStrategy<T> strategy) {
        requireNonNull(strategy, "strategy must not be null");
        if (strategy.isAdvisory()) {
            return strategy;
        }
        return new EvaluationStrategy<>() {
            @Override
            public EvaluationResult evaluate(EvaluationSample<T> sample, T output) {
                return strategy.evaluate(sample, output);
            }

            @Override
            public boolean isAdvisory() {
                return true;
            }

            @Override
            public String toString() {
                return "advisory(" + strategy + ")";
            }
        };
    }

}
