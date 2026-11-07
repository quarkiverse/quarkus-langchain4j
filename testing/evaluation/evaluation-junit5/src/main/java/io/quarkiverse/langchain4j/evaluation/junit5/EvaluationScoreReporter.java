package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;

/**
 * Test watcher that reports evaluation scores after test execution.
 * <p>
 * This extension automatically logs evaluation scores from {@link ReportConfiguration}
 * annotated fields after each test completes. It works seamlessly with
 * {@link EvaluationDisplayNameGenerator} to provide complete test reporting.
 * </p>
 */
public class EvaluationScoreReporter implements TestWatcher {

    private static final Logger LOG = Logger.getLogger(EvaluationScoreReporter.class.getName());

    @Override
    public void testSuccessful(ExtensionContext context) {
        logScoresIfAvailable(context, "PASSED");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        logScoresIfAvailable(context, "FAILED");
    }

    /**
     * Finds and logs evaluation scores from @ReportConfiguration fields.
     */
    private void logScoresIfAvailable(ExtensionContext context, String testStatus) {
        Optional<Class<?>> testClass = context.getTestClass();
        if (testClass.isEmpty()) {
            return;
        }

        Optional<Object> testInstance = context.getTestInstance();
        if (testInstance.isEmpty()) {
            return;
        }

        List<Field> reportFields = ReflectionSupport.findFields(
                testClass.get(),
                field -> field.getType().isAssignableFrom(EvaluationReport.class)
                        && field.isAnnotationPresent(ReportConfiguration.class),
                HierarchyTraversalMode.TOP_DOWN);

        for (Field field : reportFields) {
            try {
                field.setAccessible(true);
                EvaluationReport<?> report = (EvaluationReport<?>) field.get(testInstance.get());

                if (report != null) {
                    logScore(context, report, testStatus);
                }
            } catch (IllegalAccessException e) {
                LOG.warning(String.format("Failed to access report field %s: %s",
                        field.getName(), e.getMessage()));
            }
        }
    }

    private void logScore(ExtensionContext context, EvaluationReport<?> report, String testStatus) {
        String testName = context.getDisplayName();
        double score = report.score();
        int total = report.evaluations().size();
        long passed = report.evaluations().stream().filter(e -> e.passed()).count();
        long failed = total - passed;

        // Format: "Test Name: Score XX.XX% (N/M passed)"
        String message = String.format("%s [%s]: Score %.2f%% (%d/%d passed)",
                testName,
                testStatus,
                score,
                passed,
                total);

        // Add failure details if any evaluations failed
        if (failed > 0) {
            message += String.format(" - %d evaluation%s failed",
                    failed,
                    failed == 1 ? "" : "s");
        }

        // Add tag-specific scores if available
        var allTags = report.evaluations().stream()
                .flatMap(e -> e.sample().tags().stream())
                .distinct()
                .toList();

        if (!allTags.isEmpty()) {
            StringBuilder tagScores = new StringBuilder();
            for (String tag : allTags) {
                double tagScore = report.scoreForTag(tag);
                if (tagScores.length() > 0) {
                    tagScores.append(", ");
                }
                tagScores.append(String.format("%s: %.2f%%", tag, tagScore));
            }
            message += String.format(" [Tags: %s]", tagScores);
        }

        LOG.info(message);
    }
}
