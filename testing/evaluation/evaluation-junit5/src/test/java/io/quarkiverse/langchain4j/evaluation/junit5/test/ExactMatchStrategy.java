package io.quarkiverse.langchain4j.evaluation.junit5.test;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * Test strategy that checks for exact string match.
 * Used for testing @StrategyTest functionality.
 */
public class ExactMatchStrategy implements EvaluationStrategy<String> {

    @Override
    public EvaluationResult evaluate(EvaluationSample<String> sample, String output) {
        boolean matches = sample.expectedOutput().equals(output);
        return EvaluationResult.fromBoolean(matches);
    }
}
