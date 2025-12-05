package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Report formatter that generates JSON reports.
 * <p>
 * This formatter generates machine-readable JSON reports with:
 * </p>
 * <ul>
 * <li>Global score and summary statistics</li>
 * <li>Scores per tag (if samples have tags)</li>
 * <li>Detailed evaluation results</li>
 * </ul>
 */
public class JsonReportFormatter implements ReportFormatter {

    @Override
    public String format() {
        return "json";
    }

    @Override
    public String fileExtension() {
        return ".json";
    }

    @Override
    public <T> String format(EvaluationReport<T> report, Map<String, Object> config) {
        boolean pretty = (boolean) config.getOrDefault("pretty", true);
        String indent = pretty ? "  " : "";
        String newline = pretty ? "\n" : "";

        StringBuilder json = new StringBuilder();
        json.append("{").append(newline);

        // Global score and statistics
        appendField(json, "globalScore", String.format("%.2f", report.score()), indent, newline);
        json.append(",").append(newline);

        long passed = report.evaluations().stream().filter(Scorer.EvaluationResult::passed).count();
        long failed = report.evaluations().size() - passed;

        appendField(json, "totalSamples", String.valueOf(report.evaluations().size()), indent, newline);
        json.append(",").append(newline);
        appendField(json, "passed", String.valueOf(passed), indent, newline);
        json.append(",").append(newline);
        appendField(json, "failed", String.valueOf(failed), indent, newline);

        // Tag scores
        List<String> tags = report.evaluations().stream()
                .flatMap(e -> e.sample().tags().stream())
                .distinct()
                .toList();

        if (!tags.isEmpty()) {
            json.append(",").append(newline);
            json.append(indent).append("\"tagScores\": {").append(newline);

            for (int i = 0; i < tags.size(); i++) {
                String tag = tags.get(i);
                double tagScore = report.scoreForTag(tag);
                appendField(json, tag, String.format("%.2f", tagScore), indent + indent, newline);
                if (i < tags.size() - 1) {
                    json.append(",");
                }
                json.append(newline);
            }

            json.append(indent).append("}");
        }

        // Evaluations
        json.append(",").append(newline);
        json.append(indent).append("\"evaluations\": [").append(newline);

        for (int i = 0; i < report.evaluations().size(); i++) {
            Scorer.EvaluationResult<T> eval = report.evaluations().get(i);

            json.append(indent).append(indent).append("{").append(newline);
            appendField(json, "name", escapeJson(eval.sample().name()), indent + indent + indent, newline);
            json.append(",").append(newline);
            appendField(json, "passed", String.valueOf(eval.passed()), indent + indent + indent, newline);
            json.append(",").append(newline);
            appendField(json, "expectedOutput", escapeJson(String.valueOf(eval.sample().expectedOutput())),
                    indent + indent + indent, newline);
            json.append(",").append(newline);
            appendField(json, "actualOutput", escapeJson(String.valueOf(eval.result())),
                    indent + indent + indent, newline);

            if (!eval.sample().tags().isEmpty()) {
                json.append(",").append(newline);
                json.append(indent).append(indent).append(indent).append("\"tags\": [");
                for (int j = 0; j < eval.sample().tags().size(); j++) {
                    json.append("\"").append(escapeJson(eval.sample().tags().get(j))).append("\"");
                    if (j < eval.sample().tags().size() - 1) {
                        json.append(", ");
                    }
                }
                json.append("]");
            }

            json.append(newline).append(indent).append(indent).append("}");
            if (i < report.evaluations().size() - 1) {
                json.append(",");
            }
            json.append(newline);
        }

        json.append(indent).append("]").append(newline);
        json.append("}");

        return json.toString();
    }

    private void appendField(StringBuilder json, String key, String value, String indent, String newline) {
        json.append(indent).append("\"").append(key).append("\": ");
        if (isNumericOrBoolean(value)) {
            json.append(value);
        } else {
            json.append("\"").append(value).append("\"");
        }
    }

    private boolean isNumericOrBoolean(String value) {
        return value.equals("true") || value.equals("false") || value.matches("-?\\d+(\\.\\d+)?");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
