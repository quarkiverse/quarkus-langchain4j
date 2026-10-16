package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report of the evaluation of a set of samples.
 * <p>
 * Supports multiple output formats via the {@link ReportFormatter} SPI.
 * </p>
 */
public class EvaluationReport<T> {

    /**
     * z value of a two-sided 95% confidence interval under the normal approximation.
     */
    private static final double Z_95 = 1.96;

    private final List<Scorer.EvaluationResult<T>> evaluations;
    private final int trials;
    private final double score;

    /**
     * Create a new evaluation report and computes the global score.
     *
     * @param evaluations the evaluations, must not be {@code null}.
     */
    public EvaluationReport(List<Scorer.EvaluationResult<T>> evaluations) {
        this(evaluations, 1);
    }

    /**
     * Create a new evaluation report for evaluations produced by running each sample several times.
     *
     * @param evaluations the evaluations, must not be {@code null}.
     * @param trials the number of times each sample was executed, at least 1
     */
    public EvaluationReport(List<Scorer.EvaluationResult<T>> evaluations, int trials) {
        if (trials < 1) {
            throw new IllegalArgumentException("Trials must be at least 1, got: " + trials);
        }
        this.evaluations = evaluations;
        this.trials = trials;
        this.score = scoreOf(blockingEvaluationResults());
    }

    /**
     * The evaluations that count toward the score: those not produced by an advisory strategy.
     */
    private List<Scorer.EvaluationResult<T>> blockingEvaluationResults() {
        return evaluations.stream().filter(e -> !e.advisory()).toList();
    }

    /**
     * @return the global score, between 0.0 and 100.0. Advisory evaluations are not counted.
     */
    public double score() {
        return score;
    }

    /**
     * The score of the advisory evaluations, which do not count toward {@link #score()}.
     *
     * @return the percentage of passed advisory evaluations, between 0.0 and 100.0, or 0.0 if there are none
     */
    public double advisoryScore() {
        return scoreOf(evaluations.stream().filter(Scorer.EvaluationResult::advisory).toList());
    }

    /**
     * @return the number of times each sample was executed
     */
    public int trials() {
        return trials;
    }

    /**
     * Compute the score of a single sample over all its trials and strategies.
     *
     * @param name the sample name, must not be {@code null}
     * @return the percentage of passed evaluations for that sample, between 0.0 and 100.0, or 0.0 if the sample is unknown
     */
    public double scoreForSample(String name) {
        return scoreOf(blockingEvaluationResults().stream().filter(e -> e.sample().name().equals(name)).toList());
    }

    /**
     * The standard error of the global score, in percentage points.
     * <p>
     * The function under test is usually not deterministic, so the score is an estimate. The standard error is
     * computed over the per-sample scores (not over the individual trials, which are not independent) and is
     * 0.0 when the report holds fewer than two samples.
     * </p>
     *
     * @return the standard error of the global score, in percentage points
     */
    public double standardError() {
        return standardErrorOf(blockingEvaluationResults());
    }

    /**
     * The standard error of the score for a given tag, in percentage points.
     *
     * @param tag the tag, must not be {@code null}
     * @return the standard error, see {@link #standardError()}
     */
    public double standardErrorForTag(String tag) {
        return standardErrorOf(blockingEvaluationResults().stream().filter(e -> e.sample().tags().contains(tag)).toList());
    }

    /**
     * The 95% confidence interval of the global score.
     * <p>
     * A second run of the same evaluation would land inside this interval 95% of the time. Treat two scores whose
     * intervals overlap as indistinguishable. The interval is {@code [score, score]} when the report holds fewer than
     * two samples.
     * </p>
     *
     * @return the interval, clamped to {@code [0.0, 100.0]}
     */
    public Interval confidenceInterval() {
        return Interval.around(score, standardError());
    }

    /**
     * The 95% confidence interval of the score for a given tag.
     *
     * @param tag the tag, must not be {@code null}
     * @return the interval, see {@link #confidenceInterval()}
     */
    public Interval confidenceIntervalForTag(String tag) {
        return Interval.around(scoreForTag(tag), standardErrorForTag(tag));
    }

    private static double scoreOf(List<? extends Scorer.EvaluationResult<?>> evaluations) {
        return evaluations.isEmpty() ? 0.0
                : 100.0 * evaluations.stream().filter(Scorer.EvaluationResult::passed).count() / evaluations.size();
    }

    /**
     * Standard error of the mean of the per-sample pass rates, in percentage points.
     */
    private static double standardErrorOf(List<? extends Scorer.EvaluationResult<?>> evaluations) {
        Map<String, List<Scorer.EvaluationResult<?>>> bySample = new LinkedHashMap<>();
        for (Scorer.EvaluationResult<?> evaluation : evaluations) {
            bySample.computeIfAbsent(evaluation.sample().name(), n -> new ArrayList<>()).add(evaluation);
        }
        int n = bySample.size();
        if (n < 2) {
            return 0.0;
        }
        double[] rates = bySample.values().stream().mapToDouble(list -> scoreOf(list) / 100.0).toArray();
        double mean = Arrays.stream(rates).average().orElse(0.0);
        double variance = Arrays.stream(rates).map(r -> (r - mean) * (r - mean)).sum() / (n - 1);
        return 100.0 * Math.sqrt(variance / n);
    }

    /**
     * A confidence interval of a score, in percent.
     *
     * @param low the lower bound, between 0.0 and 100.0
     * @param high the upper bound, between 0.0 and 100.0
     */
    public record Interval(double low, double high) {

        private static Interval around(double score, double standardError) {
            double margin = Z_95 * standardError;
            return new Interval(Math.max(0.0, score - margin), Math.min(100.0, score + margin));
        }
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
        return scoreOf(blockingEvaluationResults().stream().filter(e -> e.sample().tags().contains(tag)).toList());
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
