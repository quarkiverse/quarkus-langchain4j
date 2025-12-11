package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationDisplayNameGenerator;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationScoreReporter;
import io.quarkiverse.langchain4j.evaluation.junit5.ReportConfiguration;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@ExtendWith(EvaluationExtension.class)
@ExtendWith(EvaluationScoreReporter.class)
@DisplayNameGeneration(EvaluationDisplayNameGenerator.class)
class EvaluationDisplayNameIT {

    @ReportConfiguration
    private EvaluationReport<String> report;

    @Test
    void testChatbotResponseQuality(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            Scorer scorer) {

        report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        assertThat(report)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(100.0);

        // This test's display name should be: "Chatbot Response Quality"
        // After execution, score should be logged: "Score 100.00% (2/2 passed)"
    }

    @Test
    void shouldHandleErrorCasesGracefully(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            Scorer scorer) {

        report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        assertThat(report).hasAllPassed();

        // Display name should be: "Handle Error Cases Gracefully"
    }

    @Test
    void verifyResponseAccuracy(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            Scorer scorer) {

        report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        assertThat(report)
                .hasEvaluationCount(2)
                .hasAllPassed();

        // Display name should be: "Response Accuracy"
    }
}
