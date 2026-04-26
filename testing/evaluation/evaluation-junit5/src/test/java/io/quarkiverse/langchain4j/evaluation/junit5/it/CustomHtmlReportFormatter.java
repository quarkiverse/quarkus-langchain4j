package io.quarkiverse.langchain4j.evaluation.junit5.it;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.ReportFormatter;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

/**
 * Custom CDI report formatter for integration testing.
 */
@ApplicationScoped
public class CustomHtmlReportFormatter implements ReportFormatter {

    @Override
    public String format() {
        return "html";
    }

    @Override
    public String fileExtension() {
        return ".html";
    }

    @Override
    public <T> String format(EvaluationReport<T> report, Map<String, Object> config) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("  <title>Evaluation Report</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("    h1 { color: #333; }\n");
        html.append("    .score { font-size: 24px; font-weight: bold; }\n");
        html.append("    .passed { color: green; }\n");
        html.append("    .failed { color: red; }\n");
        html.append("    table { border-collapse: collapse; width: 100%; margin-top: 20px; }\n");
        html.append("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("    th { background-color: #4CAF50; color: white; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <h1>Evaluation Report</h1>\n");
        html.append("  <p class=\"score\">Global Score: ").append(String.format("%.2f", report.score()))
                .append("%</p>\n");

        // Extract all unique tags from evaluations
        List<String> tags = report.evaluations().stream()
                .flatMap(e -> e.sample().tags().stream())
                .distinct()
                .toList();

        // Tag scores
        if (!tags.isEmpty()) {
            html.append("  <h2>Scores by Tag</h2>\n");
            html.append("  <ul>\n");
            for (String tag : tags) {
                double tagScore = report.scoreForTag(tag);
                html.append("    <li><strong>").append(escapeHtml(tag)).append("</strong>: ")
                        .append(String.format("%.2f", tagScore)).append("%</li>\n");
            }
            html.append("  </ul>\n");
        }

        // Evaluation details table
        html.append("  <h2>Evaluation Details</h2>\n");
        html.append("  <table>\n");
        html.append("    <tr>\n");
        html.append("      <th>Sample Name</th>\n");
        html.append("      <th>Status</th>\n");
        html.append("      <th>Score</th>\n");
        html.append("      <th>Expected</th>\n");
        html.append("      <th>Actual</th>\n");
        html.append("    </tr>\n");

        for (Scorer.EvaluationResult<T> eval : report.evaluations()) {
            html.append("    <tr>\n");
            html.append("      <td>").append(escapeHtml(eval.sample().name())).append("</td>\n");
            html.append("      <td class=\"").append(eval.passed() ? "passed" : "failed").append("\">");
            html.append(eval.passed() ? "PASSED" : "FAILED").append("</td>\n");
            html.append("      <td>").append(String.format("%.2f", eval.score() * 100)).append("%</td>\n");
            html.append("      <td>").append(escapeHtml(String.valueOf(eval.sample().expectedOutput())))
                    .append("</td>\n");
            html.append("      <td>").append(escapeHtml(String.valueOf(eval.result()))).append("</td>\n");
            html.append("    </tr>\n");
        }

        html.append("  </table>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    @Override
    public <T> void write(EvaluationReport<T> report, Path output, Map<String, Object> config) throws java.io.IOException {
        // Use default implementation from interface
        ReportFormatter.super.write(report, output, config);
    }

    @Override
    public int priority() {
        return 100; // Higher priority than built-in formatters
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
