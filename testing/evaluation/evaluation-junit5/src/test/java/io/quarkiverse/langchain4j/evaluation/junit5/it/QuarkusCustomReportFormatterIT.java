package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.ReportConfiguration;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test verifying that custom CDI report formatters work correctly
 * when using the JUnit 5 extension with Quarkus.
 */
@QuarkusTest
@ExtendWith(EvaluationExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuarkusCustomReportFormatterIT {

    @ReportConfiguration(outputDir = "target/test-reports", formats = { "markdown", "json",
            "html" }, fileName = "custom-formatter-test", includeDetails = true, pretty = true)
    private EvaluationReport<String> report;

    @AfterEach
    void logReportStatus() {
        System.out.println("After test - Report is: "
                + (report == null ? "NULL" : "NOT NULL (has " + report.evaluations().size() + " evaluations)"));
    }

    @AfterAll
    void generateReports() throws IOException {
        if (report != null) {
            Path outputDir = Paths.get("target/test-reports");
            Files.createDirectories(outputDir);

            // Manually generate reports in all supported formats
            report.saveAs(outputDir.resolve("custom-formatter-test.md"), "markdown",
                    java.util.Map.of("includeDetails", true));
            report.saveAs(outputDir.resolve("custom-formatter-test.json"), "json",
                    java.util.Map.of("pretty", true));
            report.saveAs(outputDir.resolve("custom-formatter-test.html"), "html",
                    java.util.Map.of());

            System.out.println("Generated reports in: " + outputDir);
            assertThat(outputDir).exists();
        }
    }

    @Test
    @Order(1)
    void shouldGenerateReportsInMultipleFormats(
            Scorer scorer,
            @SampleLocation("src/test/resources/test-samples.yaml") Samples<String> samples) {
        report = scorer.evaluate(samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Verify report was created
        assertThat(report).isNotNull();
        assertThat(report.evaluations()).hasSizeGreaterThan(0);
    }

    @Test
    @Order(2)
    void shouldVerifyCustomHtmlFormatterIsDiscovered() {
        io.quarkiverse.langchain4j.testing.evaluation.ReportFormatter htmlFormatter = io.quarkiverse.langchain4j.testing.evaluation.ReportFormatterRegistry
                .get("html");
        assertThat(htmlFormatter).isNotNull();
        assertThat(htmlFormatter).isInstanceOf(CustomHtmlReportFormatter.class);
        assertThat(htmlFormatter.format()).isEqualTo("html");
        assertThat(htmlFormatter.priority()).isEqualTo(100);
    }

}
