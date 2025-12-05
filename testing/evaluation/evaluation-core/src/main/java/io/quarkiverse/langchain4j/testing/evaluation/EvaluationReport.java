package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Report of the evaluation of a set of samples.
 * <p>
 * Supports multiple output formats via the {@link ReportFormatter} SPI.
 * </p>
 */
public class EvaluationReport<T> {

    private final List<Scorer.EvaluationResult<T>> evaluations;
    private final double score;

    /**
     * Create a new evaluation report and computes the global score.
     *
     * @param evaluations the evaluations, must not be {@code null}, must not be empty.
     */
    public EvaluationReport(List<Scorer.EvaluationResult<T>> evaluations) {
        this.evaluations = evaluations;
        this.score = 100.0 * evaluations.stream().filter(Scorer.EvaluationResult::passed).count() / evaluations.size();
    }

    /**
     * @return the global score, between 0.0 and 100.0.
     */
    public double score() {
        return score;
    }

    /**
     * @return the evaluations
     */
    public List<Scorer.EvaluationResult<T>> evaluations() {
        return evaluations;
    }

    /**
     * Compute the score for a given tag.
     *
     * @param tag the tag, must not be {@code null}
     * @return the score for the given tag, between 0.0 and 100.0.
     */
    public double scoreForTag(String tag) {
        return 100.0 * evaluations.stream().filter(e -> e.sample().tags().contains(tag))
                .filter(Scorer.EvaluationResult::passed).count()
                / evaluations.stream().filter(e -> e.sample().tags().contains(tag)).count();
    }

    /**
     * Save the report to a file using the specified format.
     *
     * @param output the output file path
     * @param format the format identifier (e.g., "markdown", "json")
     * @throws IOException if an error occurs while writing the report
     */
    public void saveAs(Path output, String format) throws IOException {
        saveAs(output, format, Map.of());
    }

    /**
     * Save the report to a file using the specified format with configuration.
     *
     * @param output the output file path
     * @param format the format identifier (e.g., "markdown", "json")
     * @param config optional configuration for the formatter
     * @throws IOException if an error occurs while writing the report
     */
    public void saveAs(Path output, String format, Map<String, Object> config) throws IOException {
        ReportFormatter formatter = ReportFormatterRegistry.get(format);
        formatter.write(this, output, config);
    }

    /**
     * Save the report in all supported formats.
     * <p>
     * Each format will be saved with the appropriate file extension.
     * For example, if outputDir is "target/reports" and the report is for test "MyTest",
     * it will create:
     * </p>
     * <ul>
     * <li>target/reports/report.md</li>
     * <li>target/reports/report.json</li>
     * <li>... (other formats)</li>
     * </ul>
     *
     * @param outputDir the directory where reports will be saved
     * @throws IOException if an error occurs while writing reports
     */
    public void save(Path outputDir) throws IOException {
        save(outputDir, Map.of());
    }

    /**
     * Save the report in all supported formats with configuration.
     *
     * @param outputDir the directory where reports will be saved
     * @param config optional configuration for formatters
     * @throws IOException if an error occurs while writing reports
     */
    public void save(Path outputDir, Map<String, Object> config) throws IOException {
        Files.createDirectories(outputDir);

        for (String format : ReportFormatterRegistry.supportedFormats()) {
            ReportFormatter formatter = ReportFormatterRegistry.get(format);
            Path output = outputDir.resolve("report" + formatter.fileExtension());
            formatter.write(this, output, config);
        }
    }

    /**
     * Write the report to a file using the Markdown syntax.
     * <p>
     * Deprecated: Use {@link #saveAs(Path, String)} instead.
     * </p>
     *
     * @param output the output file, must not be {@code null}
     * @throws IOException if an error occurs while writing the report
     * @deprecated Use {@link #saveAs(Path, String)} with format "markdown"
     */
    @Deprecated(forRemoval = true)
    public void writeReport(File output) throws IOException {
        writeReport(output, false);
    }

    /**
     * Write the report to a file using the Markdown syntax.
     * <p>
     * Deprecated: Use {@link #saveAs(Path, String, Map)} instead.
     * </p>
     *
     * @param output the output file, must not be {@code null}
     * @param includeResult whether to include the expectedOutput and result of the evaluation in the report
     * @throws IOException if an error occurs while writing the report
     * @deprecated Use {@link #saveAs(Path, String, Map)} with format "markdown" and config "includeDetails"
     */
    @Deprecated(forRemoval = true)
    public void writeReport(File output, boolean includeResult) throws IOException {
        saveAs(output.toPath(), "markdown", Map.of("includeDetails", includeResult));
    }

}
