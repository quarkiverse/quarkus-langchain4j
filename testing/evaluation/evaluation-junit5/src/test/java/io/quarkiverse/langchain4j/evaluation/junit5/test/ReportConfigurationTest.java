package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.ReportConfiguration;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.Parameter;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

@ExtendWith(EvaluationExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportConfigurationTest {

    private static final String OUTPUT_DIR = "target/junit-report-test";

    @ReportConfiguration(outputDir = OUTPUT_DIR, formats = { "markdown" }, fileName = "junit-report")
    private EvaluationReport<String> report;

    @BeforeAll
    static void cleanupOutputDir() throws IOException {
        Path outputDir = Paths.get(OUTPUT_DIR);
        if (Files.exists(outputDir)) {
            Files.walk(outputDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    void shouldGenerateReportAutomatically(Scorer scorer) {
        // Create test samples
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("test input"));

        EvaluationSample<String> sample = new EvaluationSample<>(
                "Test Sample",
                params,
                "expected output",
                List.of("test"));

        Samples<String> samples = new Samples<>(List.of(sample));

        // Create report
        report = scorer.evaluate(samples,
                p -> "expected output",
                (s, actual) -> EvaluationResult.fromBoolean(
                        s.expectedOutput().equals(actual)));

        assertThat(report).isNotNull();
        assertThat(report.evaluations()).hasSize(1);
    }

    @AfterAll
    void verifyReportWasCaptured() {
        // At this point, the report field should be populated
        // The actual file generation happens in EvaluationExtension.afterAll()
        // which runs AFTER this method
        assertThat(report)
                .as("Report should be captured for automatic generation")
                .isNotNull();

        assertThat(report.evaluations())
                .as("Report should contain evaluations")
                .hasSize(1);
    }
}
