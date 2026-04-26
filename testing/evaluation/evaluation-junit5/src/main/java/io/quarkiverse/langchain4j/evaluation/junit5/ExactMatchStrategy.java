package io.quarkiverse.langchain4j.evaluation.junit5;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * Default evaluation strategy that performs exact equality comparison.
 * <p>
 * This strategy simply checks if the actual output equals the expected output
 * using {@link Object#equals(Object)}.
 * </p>
 */
public class ExactMatchStrategy implements EvaluationStrategy<Object> {

    @Override
    public EvaluationResult evaluate(EvaluationSample<Object> sample, Object actual) {
        boolean matches = sample.expectedOutput() != null
                && sample.expectedOutput().equals(actual);
        return EvaluationResult.fromBoolean(matches);
    }
}
