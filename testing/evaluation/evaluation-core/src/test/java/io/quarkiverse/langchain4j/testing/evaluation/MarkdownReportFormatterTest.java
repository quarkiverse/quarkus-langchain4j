package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownReportFormatterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnCorrectFormat() {
        MarkdownReportFormatter formatter = new MarkdownReportFormatter();
        assertThat(formatter.format()).isEqualTo("markdown");
    }

    @Test
    void shouldReturnCorrectFileExtension() {
        MarkdownReportFormatter formatter = new MarkdownReportFormatter();
        assertThat(formatter.fileExtension()).isEqualTo(".md");
    }

    @Test
    void shouldFormatReportWithoutDetails() {
        // Create test data
        Parameters params1 = new Parameters();
        params1.add(new Parameter.UnnamedParameter("input1"));
        EvaluationSample<String> sample1 = new EvaluationSample<>("Sample1", params1, "expected1", List.of("tag1"));

        Parameters params2 = new Parameters();
        params2.add(new Parameter.UnnamedParameter("input2"));
        EvaluationSample<String> sample2 = new EvaluationSample<>("Sample2", params2, "expected2", List.of("tag2"));

        Scorer.EvaluationResult<String> result1 = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample1, "expected1", EvaluationResult.fromBoolean(true));
        Scorer.EvaluationResult<String> result2 = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample2, "actual2", EvaluationResult.fromBoolean(false));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result1, result2));

        // Format without details
        MarkdownReportFormatter formatter = new MarkdownReportFormatter();
        String markdown = formatter.format(report, Map.of("includeDetails", false));

        // Verify
        assertThat(markdown).contains("# Evaluation Report");
        assertThat(markdown).contains("**Global Score**: 50.00%");
        assertThat(markdown).contains("## Score per tags");
        assertThat(markdown).contains("- **tag1**: 100.00%");
        assertThat(markdown).contains("- **tag2**: 0.00%");
        assertThat(markdown).contains("## Details");
        assertThat(markdown).contains("- Sample1: PASSED");
        assertThat(markdown).contains("- Sample2: FAILED");
        assertThat(markdown).doesNotContain("#### Result");
        assertThat(markdown).doesNotContain("#### Expected Output");
    }

    @Test
    void shouldFormatReportWithDetails() {
        // Create test data
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>("TestSample", params, "expected output", List.of());

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample, "actual output", EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Format with details
        MarkdownReportFormatter formatter = new MarkdownReportFormatter();
        String markdown = formatter.format(report, Map.of("includeDetails", true));

        // Verify
        assertThat(markdown).contains("### TestSample: PASSED");
        assertThat(markdown).contains("#### Result");
        assertThat(markdown).contains("actual output");
        assertThat(markdown).contains("#### Expected Output");
        assertThat(markdown).contains("expected output");
    }

    @Test
    void shouldWriteReportToFile() throws IOException {
        // Create test data
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>("Sample", params, "expected", List.of());

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample, "actual", EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Write to file
        MarkdownReportFormatter formatter = new MarkdownReportFormatter();
        Path outputFile = tempDir.resolve("report.md");
        formatter.write(report, outputFile, Map.of());

        // Verify file was created and contains expected content
        assertThat(outputFile).exists();
        String content = Files.readString(outputFile);
        assertThat(content).contains("# Evaluation Report");
        assertThat(content).contains("**Global Score**: 100.00%");
    }

    @Test
    void shouldHandleReportWithNoTags() {
        // Create test data without tags
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>("Sample", params, "expected", List.of());

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample, "actual", EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Format
        MarkdownReportFormatter formatter = new MarkdownReportFormatter();
        String markdown = formatter.format(report, Map.of());

        // Verify no tag section
        assertThat(markdown).contains("# Evaluation Report");
        assertThat(markdown).contains("## Details");
        assertThat(markdown).doesNotContain("## Score per tags");
    }
}
