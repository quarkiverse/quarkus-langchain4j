package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Encapsulates evaluation execution logic with automatic resource management.
 * <p>
 * This interface allows for deferred execution of evaluations. You can create a runner
 * once with a specific configuration and execute it multiple times, or pass it around
 * as a reusable evaluation component.
 * </p>
 *
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * try (EvaluationRunner<String> runner = Evaluation.<String> builder()
 *         .withSamples("samples.yaml")
 *         .evaluate(params -> service.process(params.get(0)))
 *         .using(new SemanticSimilarityStrategy())
 *         .build()) {
 *
 *     // Run multiple times with same configuration
 *     EvaluationReport<String> report1 = runner.run();
 *     // ... modify service configuration ...
 *     EvaluationReport<String> report2 = runner.run();
 * }
 * // Resources automatically cleaned up
 * }</pre>
 *
 * <h2>Deferred Execution</h2>
 *
 * <pre>{@code
 * // Create runner with configuration
 * EvaluationRunner<String> runner = Evaluation.<String> builder()
 *         .withSamples(samples)
 *         .evaluate(function)
 *         .using(strategy)
 *         .build();
 *
 * // Execute later when needed
 * EvaluationReport<String> report = runner.run();
 * }</pre>
 *
 * @param <T> the type of the expected output
 */
@FunctionalInterface
public interface EvaluationRunner<T> extends AutoCloseable {

    /**
     * Execute the evaluation and return the report.
     * <p>
     * This method can be called multiple times on the same runner instance
     * to re-run the evaluation with the same configuration.
     * </p>
     *
     * @return the evaluation report
     */
    EvaluationReport<T> run();

    /**
     * Execute the evaluation and save the report to the specified directory.
     * <p>
     * The report is saved in Markdown format with a timestamped filename.
     * </p>
     *
     * @param outputDir the directory to save the report to
     * @return the evaluation report
     * @throws RuntimeException if the report cannot be saved
     */
    default EvaluationReport<T> runAndSave(Path outputDir) {
        return runAndSave(outputDir, Map.of("includeDetails", true, "includeScores", true));
    }

    /**
     * Execute the evaluation and save the report to the specified directory with custom configuration.
     *
     * @param outputDir the directory to save the report to
     * @param config configuration options for the report (e.g., includeDetails, includeScores)
     * @return the evaluation report
     * @throws RuntimeException if the report cannot be saved
     */
    default EvaluationReport<T> runAndSave(Path outputDir, Map<String, Object> config) {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory must not be null");
        }

        EvaluationReport<T> report = run();

        try {
            if (!outputDir.toFile().exists()) {
                outputDir.toFile().mkdirs();
            }

            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path reportPath = outputDir.resolve("evaluation_report_" + timestamp + ".md");

            boolean includeDetails = (boolean) config.getOrDefault("includeDetails", true);
            report.writeReport(reportPath.toFile(), includeDetails);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save report", e);
        }

        return report;
    }

    /**
     * Close any resources held by this runner.
     * <p>
     * The default implementation does nothing. Implementations should override this
     * method if they hold resources that need to be released.
     * </p>
     */
    @Override
    default void close() {
        // Default: no resources to clean up
    }
}
