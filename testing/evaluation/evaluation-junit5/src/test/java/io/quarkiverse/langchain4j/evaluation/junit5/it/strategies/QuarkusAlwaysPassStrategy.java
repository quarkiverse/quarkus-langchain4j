package io.quarkiverse.langchain4j.evaluation.junit5.it.strategies;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

/**
 * CDI bean strategy that always passes.
 * Used for testing @StrategyTest with CDI bean resolution.
 */
@ApplicationScoped
public class QuarkusAlwaysPassStrategy implements EvaluationStrategy<String> {

    @Override
    public EvaluationResult evaluate(EvaluationSample<String> sample, String output) {
        return EvaluationResult.passed(1.0);
    }
}
