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
class SuiteReporterTest2IT {

    @ReportConfiguration
    private EvaluationReport<String> regressionTestReport;

    @Test
    void testRegressionScenarios(
            @SampleLocation("src/test/resources/regression-tests.yaml") Samples<String> samples,
            Scorer scorer) {

        regressionTestReport = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Register report for suite-level reporting (works around Quarkus instance proxy issue)
        io.quarkiverse.langchain4j.evaluation.junit5.ReportRegistry.registerReport(
                getClass().getSimpleName(),
                "regressionTestReport",
                regressionTestReport);

        assertThat(regressionTestReport)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(100.0);
    }
}
