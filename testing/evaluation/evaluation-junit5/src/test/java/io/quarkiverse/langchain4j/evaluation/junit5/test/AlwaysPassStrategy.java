package io.quarkiverse.langchain4j.evaluation.junit5.test;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * Test strategy that always passes.
 * Used for testing @StrategyTest functionality.
 */
public class AlwaysPassStrategy implements EvaluationStrategy<String> {

    @Override
    public EvaluationResult evaluate(EvaluationSample<String> sample, String output) {
        return EvaluationResult.passed(1.0);
    }
}
