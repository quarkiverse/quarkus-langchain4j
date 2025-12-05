package io.quarkiverse.langchain4j.sample.chatbot;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.ReportConfiguration;
import io.quarkiverse.langchain4j.evaluation.junit5.ReportRegistry;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.evaluation.junit5.SuiteEvaluationReporter;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.langchain4j.testing.evaluation.similarity.SemanticSimilarityStrategy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates suite-level reporting with automatic report generation.
 * Reports are saved to files in multiple formats (Markdown, JSON).
 */
@QuarkusTest
@Evaluate
@ExtendWith(SuiteEvaluationReporter.class)
public class SuiteReportingTest {

    @Inject
    CustomerSupportBot bot;

    /**
     * Configure automatic report generation.
     * The report will be saved to target/reports/smoke-test-report.{md,json}
     */
    @ReportConfiguration(outputDir = "target/reports", fileName = "smoke-test-report", formats = { "markdown",
            "json" }, includeDetails = true, pretty = true)
    private EvaluationReport<String> smokeTestReport;

    /**
     * Configure report for comprehensive tests.
     */
    @ReportConfiguration(outputDir = "target/reports", fileName = "comprehensive-report", formats = { "markdown", "json" })
    private EvaluationReport<String> comprehensiveReport;

    @Test
    void smokeTestEvaluation(
            @ScorerConfiguration(concurrency = 3) Scorer scorer,
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples) {

        smokeTestReport = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.75));

        // Register for suite-level reporting
        ReportRegistry.registerReport(
                getClass().getSimpleName(),
                "smokeTestReport",
                smokeTestReport);

        assertThat(smokeTestReport)
                .hasScoreGreaterThan(60.0);

        System.out.println("Smoke test report saved to target/reports/smoke-test-report.md");
    }

    @Test
    void comprehensiveEvaluation(
            Scorer scorer,
            @SampleLocation("src/test/resources/customer-support-samples.yaml") Samples<String> samples) {

        comprehensiveReport = scorer.evaluate(
                samples,
                params -> bot.chat(params.get(0)),
                new SemanticSimilarityStrategy(0.85));

        // Register for suite-level reporting
        ReportRegistry.registerReport(
                getClass().getSimpleName(),
                "comprehensiveReport",
                comprehensiveReport);

        assertThat(comprehensiveReport)
                .hasScoreGreaterThan(75.0)
                .hasAtLeastPassedEvaluations(4);

        System.out.println("Comprehensive report saved to target/reports/comprehensive-report.md");
    }

    /*
     * After all tests complete, the following files will be generated:
     * - target/reports/smoke-test-report.md (Markdown format)
     * - target/reports/smoke-test-report.json (JSON format)
     * - target/reports/comprehensive-report.md
     * - target/reports/comprehensive-report.json
     * - target/evaluation-suite-report.md (Combined suite report)
     * - target/evaluation-suite-report.json (Combined suite JSON)
     */
}
