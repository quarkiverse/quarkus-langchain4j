package io.quarkiverse.langchain4j.evaluation.junit5;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;

/**
 * Suite-level evaluation reporter that aggregates reports across all test classes.
 * <p>
 * This extension collects evaluation reports from all tests in the suite and generates
 * a comprehensive suite report at the end. Reports are gathered from {@link ReportConfiguration}
 * annotated fields across all test classes.
 * </p>
 */
public class SuiteEvaluationReporter implements BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final Logger LOG = Logger.getLogger(SuiteEvaluationReporter.class.getName());

    // Shared state across all test classes
    private static final Map<String, EvaluationReport<?>> SUITE_REPORTS = new ConcurrentHashMap<>();
    private static final AtomicBoolean SUITE_REPORT_GENERATED = new AtomicBoolean(false);
    private static final Object LOCK = new Object();

    // Key for storing reports in ExtensionContext.Store
    private static final String REPORTS_KEY = "suiteReports";

    // Configuration
    private static final String REPORT_DIR_PROPERTY = "evaluation.suite.report.dir";
    private static final String DEFAULT_REPORT_DIR = "target";
    private static final String REPORT_FILENAME = "evaluation-suite-report";

    static {
        // Register shutdown hook to ensure report generation even if JVM exits unexpectedly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!SUITE_REPORTS.isEmpty() && !SUITE_REPORT_GENERATED.get()) {
                generateSuiteReportInternal();
            }
        }));
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Nothing to do before each test
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // Nothing to do after each test - reports are collected from the registry in afterAll
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // Collect reports from ReportRegistry (works around Quarkus proxy instances)
        collectReportsFromEvaluationExtension(context);

        // Don't generate reports here - let the shutdown hook handle it
        // This ensures all test classes have completed before generating the report
    }

    /**
     * Collects reports from the ReportRegistry.
     * This works around Quarkus test instance proxy issues.
     */
    private void collectReportsFromEvaluationExtension(ExtensionContext context) {
        context.getTestClass().ifPresent(testClass -> {
            Map<String, EvaluationReport<?>> reports = ReportRegistry.getReportsForClass(
                    testClass.getSimpleName());

            for (Map.Entry<String, EvaluationReport<?>> entry : reports.entrySet()) {
                String fieldName = entry.getKey();
                EvaluationReport<?> report = entry.getValue();

                String reportKey = String.format("%s.%s", testClass.getSimpleName(), fieldName);
                SUITE_REPORTS.put(reportKey, report);
                LOG.fine(String.format("Collected report: %s (score: %.2f%%)",
                        reportKey, report.score()));
            }
        });
    }

    /**
     * Collects evaluation reports from @ReportConfiguration fields in the test class.
     */
    private void collectReportsFromClass(ExtensionContext context) {
        context.getTestClass().ifPresent(testClass -> {
            LOG.info("Looking for @ReportConfiguration fields in: " + testClass.getSimpleName());

            Object testInstance = context.getRequiredTestInstance();
            LOG.info("Test instance class: " + testInstance.getClass().getName());
            LOG.info("Test class: " + testClass.getName());

            // Look for fields on the actual instance class, which might be different from testClass
            // in case of Quarkus proxies
            Class<?> instanceClass = testInstance.getClass();

            List<Field> reportFields = ReflectionSupport.findFields(
                    instanceClass,
                    field -> field.getType().isAssignableFrom(EvaluationReport.class)
                            && field.isAnnotationPresent(ReportConfiguration.class),
                    HierarchyTraversalMode.TOP_DOWN);

            LOG.info("Found " + reportFields.size() + " @ReportConfiguration fields on instance class");

            for (Field field : reportFields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(testInstance);
                    LOG.info(String.format("Field %s value: %s (type: %s)",
                            field.getName(),
                            fieldValue,
                            fieldValue != null ? fieldValue.getClass().getName() : "null"));

                    EvaluationReport<?> report = (EvaluationReport<?>) fieldValue;

                    if (report != null) {
                        String reportKey = String.format("%s.%s",
                                testClass.getSimpleName(),
                                field.getName());
                        SUITE_REPORTS.put(reportKey, report);
                        LOG.info(String.format("Collected report: %s (score: %.2f%%)",
                                reportKey, report.score()));
                    } else {
                        LOG.warning("Report field " + field.getName() + " is null");
                    }
                } catch (IllegalAccessException e) {
                    LOG.warning(String.format("Failed to access report field %s.%s: %s",
                            testClass.getSimpleName(), field.getName(), e.getMessage()));
                } catch (Exception e) {
                    LOG.warning(String.format("Error processing field %s.%s: %s",
                            testClass.getSimpleName(), field.getName(), e.getMessage()));
                }
            }
        });
    }

    /**
     * Determines if suite report should be generated.
     * This is called after each test class completes.
     */
    private boolean shouldGenerateSuiteReport(ExtensionContext context) {
        // Don't generate report from afterAll - let the shutdown hook handle it
        // This ensures all test classes have completed before generating the report
        return false;
    }

    /**
     * Generates the suite-level evaluation report.
     */
    private void generateSuiteReport() {
        synchronized (LOCK) {
            if (SUITE_REPORT_GENERATED.compareAndSet(false, true)) {
                generateSuiteReportInternal();
            }
        }
    }

    /**
     * Internal method to generate suite report.
     */
    private static void generateSuiteReportInternal() {
        if (SUITE_REPORTS.isEmpty()) {
            return;
        }

        try {
            Path outputDir = getOutputDirectory();
            Files.createDirectories(outputDir);

            // Generate summary
            SuiteReportSummary summary = calculateSummary();

            // Generate Markdown report
            Path mdPath = outputDir.resolve(REPORT_FILENAME + ".md");
            generateMarkdownReport(mdPath, summary);
            LOG.info(String.format("Generated suite evaluation report: %s", mdPath));

            // Generate JSON report
            Path jsonPath = outputDir.resolve(REPORT_FILENAME + ".json");
            generateJsonReport(jsonPath, summary);
            LOG.info(String.format("Generated suite evaluation report: %s", jsonPath));

        } catch (IOException e) {
            LOG.severe(String.format("Failed to generate suite report: %s", e.getMessage()));
        }
    }

    /**
     * Calculates summary statistics across all reports.
     */
    private static SuiteReportSummary calculateSummary() {
        int totalReports = SUITE_REPORTS.size();
        int totalEvaluations = 0;
        int totalPassed = 0;
        double totalScore = 0.0;

        for (EvaluationReport<?> report : SUITE_REPORTS.values()) {
            int reportEvaluations = report.evaluations().size();
            long reportPassed = report.evaluations().stream().filter(e -> e.passed()).count();

            totalEvaluations += reportEvaluations;
            totalPassed += reportPassed;
            totalScore += report.score();
        }

        double averageScore = totalReports > 0 ? totalScore / totalReports : 0.0;

        return new SuiteReportSummary(
                totalReports,
                totalEvaluations,
                totalPassed,
                totalEvaluations - totalPassed,
                averageScore,
                Instant.now());
    }

    /**
     * Generates Markdown format suite report.
     */
    private static void generateMarkdownReport(Path output, SuiteReportSummary summary) throws IOException {
        StringBuilder md = new StringBuilder();

        md.append("# Evaluation Suite Report\n\n");
        md.append(String.format("**Generated**: %s\n\n", summary.timestamp));

        md.append("## Summary\n\n");
        md.append(String.format("- **Total Test Classes**: %d\n", summary.totalReports));
        md.append(String.format("- **Total Evaluations**: %d\n", summary.totalEvaluations));
        md.append(String.format("- **Passed**: %d\n", summary.totalPassed));
        md.append(String.format("- **Failed**: %d\n", summary.totalFailed));
        md.append(String.format("- **Average Score**: %.2f%%\n\n", summary.averageScore));

        md.append("## Individual Reports\n\n");
        md.append("| Test Class | Evaluations | Passed | Failed | Score |\n");
        md.append("|------------|-------------|--------|--------|-------|\n");

        SUITE_REPORTS.forEach((key, report) -> {
            int evals = report.evaluations().size();
            long passed = report.evaluations().stream().filter(e -> e.passed()).count();
            long failed = evals - passed;

            md.append(String.format("| %s | %d | %d | %d | %.2f%% |\n",
                    key, evals, passed, failed, report.score()));
        });

        Files.writeString(output, md.toString());
    }

    /**
     * Generates JSON format suite report.
     */
    private static void generateJsonReport(Path output, SuiteReportSummary summary) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(String.format("  \"timestamp\": \"%s\",\n", summary.timestamp));
        json.append("  \"summary\": {\n");
        json.append(String.format("    \"totalReports\": %d,\n", summary.totalReports));
        json.append(String.format("    \"totalEvaluations\": %d,\n", summary.totalEvaluations));
        json.append(String.format("    \"totalPassed\": %d,\n", summary.totalPassed));
        json.append(String.format("    \"totalFailed\": %d,\n", summary.totalFailed));
        json.append(String.format("    \"averageScore\": %.2f\n", summary.averageScore));
        json.append("  },\n");
        json.append("  \"reports\": [\n");

        List<String> reportEntries = new ArrayList<>();
        SUITE_REPORTS.forEach((key, report) -> {
            int evals = report.evaluations().size();
            long passed = report.evaluations().stream().filter(e -> e.passed()).count();
            long failed = evals - passed;

            String entry = String.format(
                    "    {\n" +
                            "      \"testClass\": \"%s\",\n" +
                            "      \"evaluations\": %d,\n" +
                            "      \"passed\": %d,\n" +
                            "      \"failed\": %d,\n" +
                            "      \"score\": %.2f\n" +
                            "    }",
                    key, evals, passed, failed, report.score());
            reportEntries.add(entry);
        });

        json.append(String.join(",\n", reportEntries));
        json.append("\n  ]\n");
        json.append("}\n");

        Files.writeString(output, json.toString());
    }

    /**
     * Gets the output directory for suite reports.
     */
    private static Path getOutputDirectory() {
        String dirProperty = System.getProperty(REPORT_DIR_PROPERTY);
        String dir = dirProperty != null ? dirProperty : DEFAULT_REPORT_DIR;
        return Paths.get(dir);
    }

    /**
     * Summary statistics for the test suite.
     */
    private static class SuiteReportSummary {
        final int totalReports;
        final int totalEvaluations;
        final int totalPassed;
        final int totalFailed;
        final double averageScore;
        final Instant timestamp;

        SuiteReportSummary(int totalReports, int totalEvaluations, int totalPassed,
                int totalFailed, double averageScore, Instant timestamp) {
            this.totalReports = totalReports;
            this.totalEvaluations = totalEvaluations;
            this.totalPassed = totalPassed;
            this.totalFailed = totalFailed;
            this.averageScore = averageScore;
            this.timestamp = timestamp;
        }
    }

    /**
     * Clears collected reports. Useful for testing.
     */
    static void clearReports() {
        SUITE_REPORTS.clear();
        SUITE_REPORT_GENERATED.set(false);
    }
}
