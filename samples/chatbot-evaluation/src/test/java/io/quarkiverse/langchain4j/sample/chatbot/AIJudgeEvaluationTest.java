package io.quarkiverse.langchain4j.sample.chatbot;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.langchain4j.testing.evaluation.judge.AiJudgeStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.similarity.SemanticSimilarityStrategy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates using AI as a judge to evaluate chatbot responses.
 * This approach uses an LLM to judge whether responses are acceptable,
 * which is useful for more nuanced evaluation beyond simple similarity.
 */
@QuarkusTest
@Evaluate
public class AIJudgeEvaluationTest {

    @Inject
    CustomerSupportBot bot;

    @Inject
    ChatModel model;

    @Test
    void evaluateWithAIJudge(
            Scorer scorer,
            @SampleLocation("src/test/resources/customer-support-samples.yaml") Samples<String> samples) {

        // Use AI judge strategy for more sophisticated evaluation
        EvaluationReport<String> report = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new AiJudgeStrategy(model));

        assertThat(report)
                .hasScoreGreaterThanOrEqualTo(50.0)
                .hasAtLeastPassedEvaluations(3);

        // Print detailed results
        report.evaluations().forEach(eval -> {
            System.out.printf("%s: %s (score: %.2f)%n",
                    eval.sample().name(),
                    eval.passed() ? "PASS" : "FAIL",
                    eval.score() * 100);
            if (eval.explanation() != null) {
                System.out.printf("  Explanation: %s%n", eval.explanation());
            }
        });
    }

    @Test
    void compareStrategies(
            Scorer scorer,
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples) {

        // Evaluate with semantic similarity
        EvaluationReport<String> semanticReport = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.85));

        // Evaluate with AI judge
        EvaluationReport<String> aiJudgeReport = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new AiJudgeStrategy(model));

        System.out.printf("Semantic Similarity Score: %.2f%%%n", semanticReport.score());
        System.out.printf("AI Judge Score: %.2f%%%n", aiJudgeReport.score());

        // Both should have reasonable scores
        assertThat(semanticReport).hasScoreGreaterThan(60.0);
        assertThat(aiJudgeReport).hasScoreGreaterThan(60.0);
    }

    @Test
    void evaluateWithBothStrategies(
            Scorer scorer,
            @SampleLocation("src/test/resources/customer-support-samples.yaml") Samples<String> samples) {

        // Apply both strategies - sample must pass both to be considered successful
        EvaluationReport<String> report = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.80),
                new AiJudgeStrategy(model));

        // Since each sample is evaluated by both strategies,
        // we'll have 2x the number of evaluations
        assertThat(report)
                .hasAtLeastPassedEvaluations(8); // At least half should pass
    }
}
