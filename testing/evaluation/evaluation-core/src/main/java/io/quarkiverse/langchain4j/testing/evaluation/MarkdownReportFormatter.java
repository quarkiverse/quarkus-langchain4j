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
        buffer.append("**Global Score**: ").append(String.format("%.2f", report.score())).append("%\n\n");

        // Extract all unique tags from evaluations
        List<String> tags = report.evaluations().stream()
                .flatMap(e -> e.sample().tags().stream())
                .distinct()
                .toList();

        // Score per tags section
        if (!tags.isEmpty()) {
            buffer.append("## Score per tags\n\n");
            for (String tag : tags) {
                double tagScore = report.scoreForTag(tag);
                buffer.append("- **").append(tag).append("**: ")
                        .append(String.format("%.2f", tagScore)).append("%\n");
            }
            buffer.append("\n");
        }

        // Details section
        buffer.append("## Details\n\n");
        String detailHeader = includeDetails ? "### " : "- ";

        for (Scorer.EvaluationResult<T> evaluation : report.evaluations()) {
            buffer.append(detailHeader)
                    .append(evaluation.sample().name())
                    .append(": ")
                    .append(evaluation.passed() ? "PASSED" : "FAILED")
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
}
