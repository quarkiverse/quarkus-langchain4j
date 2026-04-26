package io.quarkiverse.langchain4j.sample.chatbot;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.testing.evaluation.Evaluation;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.similarity.SemanticSimilarityStrategy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates the fluent builder API for creating evaluations.
 * This provides a more readable, chainable way to define evaluations.
 */
@QuarkusTest
@Evaluate
public class FluentAPIEvaluationTest {

    @Inject
    CustomerSupportBot bot;

    @Test
    void evaluateWithFluentBuilder() {
        // Use the fluent builder API
        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples("src/test/resources/customer-support-samples.yaml")
                .withConcurrency(5)
                .evaluate(params -> bot.chat(params.get(0)))
                .using(new SemanticSimilarityStrategy(0.85))
                .run();

        assertThat(report)
                .hasScoreGreaterThan(75.0)
                .hasAtLeastPassedEvaluations(4);
    }

    @Test
    void evaluateFilteredByTags() {
        // Evaluate only samples with specific tags
        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples("src/test/resources/customer-support-samples.yaml")
                .withTags("critical") // Only evaluate critical samples
                .evaluate(params -> bot.chat(params.get(0)))
                .using(new SemanticSimilarityStrategy(0.75))
                .run();

        assertThat(report)
                .hasScoreGreaterThan(70.0)
                .hasAtLeastPassedEvaluations(2);
    }

    @Test
    void evaluateWithMultipleStrategies() {
        // Apply multiple strategies to the same samples
        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples("src/test/resources/smoke-tests.yaml")
                .evaluate(params -> bot.chat(params.get(0)))
                .using(new SemanticSimilarityStrategy(0.80))
                .using(new SemanticSimilarityStrategy(0.90)) // Stricter threshold
                .run();

        // Each sample is evaluated against each strategy
        assertThat(report)
                .hasAtLeastPassedEvaluations(3); // At least some should pass
    }

    @Test
    void deferredExecution() {
        // Build an evaluation definition that can be executed later
        var evaluationRunner = Evaluation.<String> builder()
                .withSamples("src/test/resources/smoke-tests.yaml")
                .evaluate(params -> bot.chat(params.get(0)))
                .using(new SemanticSimilarityStrategy(0.75))
                .build();

        // Execute the evaluation
        EvaluationReport<String> report1 = evaluationRunner.run();
        assertThat(report1).hasScoreGreaterThan(60.0);

        // Can execute again (e.g., after configuration changes)
        EvaluationReport<String> report2 = evaluationRunner.run();
        assertThat(report2).hasScoreGreaterThan(60.0);
    }
}
