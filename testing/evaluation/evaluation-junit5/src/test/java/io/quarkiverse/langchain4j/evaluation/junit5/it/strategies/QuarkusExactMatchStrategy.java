package io.quarkiverse.langchain4j.evaluation.junit5.it.strategies;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * CDI bean strategy that checks for exact match.
 * Used for testing @StrategyTest with CDI bean resolution.
 */
@ApplicationScoped
public class QuarkusExactMatchStrategy implements EvaluationStrategy<String> {

    @Override
    public EvaluationResult evaluate(EvaluationSample<String> sample, String output) {
        boolean matches = sample.expectedOutput().equals(output);
        return EvaluationResult.fromBoolean(matches);
    }
}
