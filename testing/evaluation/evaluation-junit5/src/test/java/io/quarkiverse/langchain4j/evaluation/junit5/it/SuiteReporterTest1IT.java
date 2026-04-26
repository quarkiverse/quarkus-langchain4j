package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.ReportConfiguration;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.SuiteEvaluationReporter;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@ExtendWith(EvaluationExtension.class)
@ExtendWith(SuiteEvaluationReporter.class)
class SuiteReporterTest1IT {

    @ReportConfiguration
    private EvaluationReport<String> smokeTestReport;

    @Test
    void testSmokeScenarios(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            Scorer scorer) {

        smokeTestReport = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Register report for suite-level reporting (works around Quarkus instance proxy issue)
        io.quarkiverse.langchain4j.evaluation.junit5.ReportRegistry.registerReport(
                getClass().getSimpleName(),
                "smokeTestReport",
                smokeTestReport);

        assertThat(smokeTestReport)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(100.0);
    }
}
