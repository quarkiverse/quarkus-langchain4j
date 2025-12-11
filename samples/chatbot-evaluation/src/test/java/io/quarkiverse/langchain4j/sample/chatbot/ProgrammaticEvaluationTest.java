package io.quarkiverse.langchain4j.sample.chatbot;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.langchain4j.testing.evaluation.similarity.SemanticSimilarityStrategy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Evaluate
public class ProgrammaticEvaluationTest {

    @Inject
    CustomerSupportBot bot;

    @Test
    void evaluateWithSemanticSimilarity(
            @ScorerConfiguration(concurrency = 3) Scorer scorer,
            @SampleLocation("src/test/resources/customer-support-samples.yaml") Samples<String> samples) {

        // Evaluate using semantic similarity strategy with 85% threshold
        EvaluationReport<String> report = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.85));

        // Assert using fluent assertions
        assertThat(report)
                .hasScoreGreaterThan(80.0)
                .hasAtLeastPassedEvaluations(4)
                .hasAtMostFailedEvaluations(2);

        // Print summary
        System.out.println("=== Evaluation Results ===");
        System.out.printf("Overall Score: %.2f%%%n", report.score());
    }

    @Test
    void evaluateCriticalSamplesOnly(
            Scorer scorer,
            @SampleLocation("src/test/resources/customer-support-samples.yaml") Samples<String> samples) {

        // Filter and evaluate only samples tagged as "critical"
        var criticalSamples = samples.stream()
                .filter(sample -> sample.tags().contains("critical"))
                .toList();

        Samples<String> filtered = new Samples<>(criticalSamples);

        EvaluationReport<String> report = scorer.evaluate(
                filtered,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.70));

        assertThat(report)
                .hasScoreGreaterThan(60.0)
                .hasAtLeastPassedEvaluations(2);
    }

    @Test
    void evaluateSmokeTests(
            Scorer scorer,
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples) {

        // Quick smoke tests with lower threshold
        EvaluationReport<String> report = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.75));

        assertThat(report)
                .hasSomePassed()
                .hasScoreGreaterThan(60.0);
    }
}
