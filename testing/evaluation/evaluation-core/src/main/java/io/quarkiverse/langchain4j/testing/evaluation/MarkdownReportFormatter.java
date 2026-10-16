package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Report formatter that generates Markdown reports.
 * <p>
 * This formatter generates human-readable Markdown reports with:
 * </p>
 * <ul>
 * <li>Global score summary</li>
 * <li>Scores per tag (if samples have tags)</li>
 * <li>Detailed evaluation results (optional)</li>
 * </ul>
 */
public class MarkdownReportFormatter implements ReportFormatter {

    @Override
    public String format() {
        return "markdown";
    }

    @Override
    public String fileExtension() {
        return ".md";
    }

    @Override
    public <T> String format(EvaluationReport<T> report, Map<String, Object> config) {
        boolean includeDetails = (boolean) config.getOrDefault("includeDetails", false);

        StringBuilder buffer = new StringBuilder();
        buffer.append("# Evaluation Report\n\n");
        buffer.append("**Global Score**: ").append(String.format("%.2f", report.score())).append("%")
                .append(interval(report.confidenceInterval())).append("\n\n");
        if (report.trials() > 1) {
            buffer.append("**Trials per sample**: ").append(report.trials()).append("\n\n");
        }

        List<Scorer.EvaluationResult<T>> blockingResults = report.evaluations().stream().filter(e -> !e.advisory()).toList();
        List<Scorer.EvaluationResult<T>> advisoryResults = report.evaluations().stream()
                .filter(Scorer.EvaluationResult::advisory)
                .toList();

        // Extract all unique tags from the evaluations that count toward the score
        List<String> tags = blockingResults.stream()
                .flatMap(e -> e.sample().tags().stream())
                .distinct()
                .toList();

        // Score per tags section
        if (!tags.isEmpty()) {
            buffer.append("## Score per tags\n\n");
            for (String tag : tags) {
                double tagScore = report.scoreForTag(tag);
                buffer.append("- **").append(tag).append("**: ")
                        .append(String.format("%.2f", tagScore)).append("%")
                        .append(interval(report.confidenceIntervalForTag(tag))).append("\n");
            }
            buffer.append("\n");
        }

        // Advisory section: reported, but not counted
        if (!advisoryResults.isEmpty()) {
            buffer.append("## Advisory\n\n");
            buffer.append("**Advisory Score**: ").append(String.format("%.2f", report.advisoryScore()))
                    .append("% (not counted in the global score)\n\n");
            for (Scorer.EvaluationResult<T> evaluation : advisoryResults) {
                buffer.append("- ").append(evaluation.sample().name()).append(": ")
                        .append(evaluation.passed() ? "PASSED" : "FAILED").append(" (")
                        .append(String.format("%.2f", evaluation.score() * 100)).append("%)");
                if (evaluation.explanation() != null && !evaluation.explanation().isEmpty()) {
                    buffer.append(" - ").append(evaluation.explanation());
                }
                buffer.append("\n");
            }
            buffer.append("\n");
        }

        // Details section
        buffer.append("## Details\n\n");
        String detailHeader = includeDetails ? "### " : "- ";

        if (report.trials() > 1) {
            List<String> sampleNames = blockingResults.stream()
                    .map(e -> e.sample().name())
                    .distinct()
                    .toList();
            for (String name : sampleNames) {
                long total = blockingResults.stream().filter(e -> e.sample().name().equals(name)).count();
                long passed = blockingResults.stream()
                        .filter(e -> e.sample().name().equals(name) && e.passed()).count();
                buffer.append("- ").append(name).append(": ")
                        .append(passed).append("/").append(total).append(" evaluations passed").append(" (")
                        .append(String.format("%.2f", report.scoreForSample(name))).append("%)")
                        .append("\n");
            }
            if (!includeDetails) {
                return buffer.toString();
            }
            buffer.append("\n");
        }

        for (Scorer.EvaluationResult<T> evaluation : blockingResults) {
            buffer.append(detailHeader)
                    .append(evaluation.sample().name())
                    .append(": ")
                    .append(evaluation.passed() ? "PASSED" : "FAILED").append(" (")
                    .append(String.format("%.2f", evaluation.score() * 100)).append("%").append(")")
                    .append("\n");

            if (includeDetails) {
                buffer.append("#### Result\n");
                buffer.append(evaluation.result()).append("\n");
                buffer.append("#### Expected Output\n");
                buffer.append(evaluation.sample().expectedOutput()).append("\n");
            }
        }

        return buffer.toString();
    }

    private static String interval(EvaluationReport.Interval interval) {
        return String.format(" (95%% CI: %.2f%% - %.2f%%)", interval.low(), interval.high());
    }
}
