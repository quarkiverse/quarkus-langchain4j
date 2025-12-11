package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Service Provider Interface for formatting evaluation reports.
 * <p>
 * Implementations can generate reports in various formats (Markdown, JSON, HTML, PDF, etc.).
 * Formatters are discovered using a hybrid approach:
 * </p>
 * <ul>
 * <li>CDI beans (when running in Quarkus) - supports dependency injection</li>
 * <li>ServiceLoader (always) - ensures formatters are available in all contexts</li>
 * </ul>
 */
public interface ReportFormatter {

    /**
     * Get the format identifier (e.g., "markdown", "json", "html").
     * <p>
     * This identifier is used to select the appropriate formatter when generating reports.
     * </p>
     *
     * @return the format identifier in lowercase
     */
    String format();

    /**
     * Get the file extension for this format (e.g., ".md", ".json", ".html").
     * <p>
     * The extension should include the leading dot.
     * </p>
     *
     * @return the file extension including the dot
     */
    String fileExtension();

    /**
     * Format the evaluation report.
     *
     * @param report the evaluation report to format
     * @param config optional configuration for formatting (e.g., includeDetails, pretty)
     * @param <T> the type of expected output
     * @return formatted report as string
     */
    <T> String format(EvaluationReport<T> report, Map<String, Object> config);

    /**
     * Write the formatted report to a file.
     * <p>
     * Default implementation writes the string output of {@link #format(EvaluationReport, Map)}
     * to the specified path.
     * </p>
     *
     * @param report the evaluation report
     * @param output the output file path
     * @param config optional configuration
     * @param <T> the type of expected output
     * @throws IOException if writing fails
     */
    default <T> void write(EvaluationReport<T> report, Path output, Map<String, Object> config)
            throws IOException {
        String content = format(report, config);
        Files.writeString(output, content);
    }

    /**
     * Get the priority of this formatter (higher = higher priority).
     * <p>
     * Used when multiple formatters support the same format identifier.
     * Default priority is 0.
     * </p>
     *
     * @return priority value (default: 0)
     */
    default int priority() {
        return 0;
    }
}
