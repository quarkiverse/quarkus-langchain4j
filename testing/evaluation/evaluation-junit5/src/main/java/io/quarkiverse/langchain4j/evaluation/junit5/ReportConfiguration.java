package io.quarkiverse.langchain4j.evaluation.junit5;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Configures automatic report generation for evaluation reports.
 * <p>
 * When applied to an {@link io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport} field,
 * the extension will automatically save the report in the specified formats after all tests complete.
 * </p>
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;ExtendWith(EvaluationExtension.class)
 *     class MyEvaluationTest {
 *         &#64;ReportConfiguration(outputDir = "target/evaluation-reports", formats = { "markdown", "json" })
 *         private EvaluationReport<String> report;
 *
 *         @Test
 *         void runEvaluation(Scorer scorer, @SampleLocation("samples.yaml") Samples<String> samples) {
 *             report = scorer.evaluate(samples, input -> "response", new ExactMatchStrategy<>());
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({ FIELD })
public @interface ReportConfiguration {

    /**
     * The output directory where reports will be saved.
     * <p>
     * Can be relative (e.g., "target/reports") or absolute.
     * </p>
     *
     * @return the output directory path
     */
    String outputDir() default "target/evaluation-reports";

    /**
     * The report formats to generate.
     * <p>
     * Supported formats depend on the registered {@link io.quarkiverse.langchain4j.testing.evaluation.ReportFormatter}
     * implementations. By default, "markdown" and "json" are available.
     * </p>
     * <p>
     * If empty, all supported formats will be generated.
     * </p>
     *
     * @return array of format identifiers (e.g., ["markdown", "json"])
     */
    String[] formats() default {};

    /**
     * The base name for the report files (without extension).
     * <p>
     * If not specified, the test class name will be used.
     * </p>
     * <p>
     * For example, if {@code fileName = "my-evaluation"} and formats are ["markdown", "json"],
     * the following files will be created:
     * </p>
     * <ul>
     * <li>{@code my-evaluation.md}</li>
     * <li>{@code my-evaluation.json}</li>
     * </ul>
     *
     * @return the base file name
     */
    String fileName() default "";

    /**
     * Whether to include detailed evaluation results in the report.
     * <p>
     * This is a hint for report formatters that support detail levels (like markdown).
     * </p>
     *
     * @return true to include details, false for summary only
     */
    boolean includeDetails() default false;

    /**
     * Whether to pretty-print the reports (for formats that support it, like JSON).
     *
     * @return true to enable pretty printing
     */
    boolean pretty() default true;
}
