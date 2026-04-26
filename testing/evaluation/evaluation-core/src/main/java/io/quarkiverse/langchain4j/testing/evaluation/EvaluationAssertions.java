package io.quarkiverse.langchain4j.testing.evaluation;

/**
 * Entry point for fluent assertions on {@link EvaluationReport}.
 * <p>
 * This class provides a fluent API for asserting evaluation results with rich
 * failure messages that include scores, explanations, and detailed sample information.
 * </p>
 */
public class EvaluationAssertions {

    private EvaluationAssertions() {
        // Utility class - prevent instantiation
    }

    /**
     * Create assertion object for an evaluation report.
     *
     * @param actual the evaluation report to assert on
     * @return assertion object for fluent assertions
     */
    public static EvaluationReportAssert assertThat(EvaluationReport<?> actual) {
        return new EvaluationReportAssert(actual);
    }
}
