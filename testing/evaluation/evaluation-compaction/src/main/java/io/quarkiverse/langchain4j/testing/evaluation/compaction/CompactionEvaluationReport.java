package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The outcome of a {@link CompactionEvaluation}: one result per evaluated profile plus the {@value #FULL_HISTORY} and
 * {@value #NO_HISTORY} baselines, and every graded answer.
 */
public record CompactionEvaluationReport(String conversation, List<Result> results, List<Answer> answers) {

    public static final String FULL_HISTORY = "full-history";
    public static final String NO_HISTORY = "no-history";

    public enum Grade {
        CORRECT(1.0),
        PARTIAL(0.5),
        WRONG(0.0);

        public final double score;

        Grade(double score) {
            this.score = score;
        }
    }

    /**
     * @param recall mean grade of the answers, from 0 to 1: profiles and {@value #NO_HISTORY} are graded against the
     *        full-history answer, {@value #FULL_HISTORY} against the expected facts ({@code NaN} if there are none)
     * @param promptTokens tokens sent to the model over the whole conversation, one call per recorded assistant
     *        message
     * @param viewTokens tokens of the context when the questions are asked
     * @param savings share of prompt tokens saved over the full history
     * @param summaries the text of the summaries in the context when the questions are asked
     */
    public record Result(String name, double recall, long promptTokens, int viewTokens, double savings,
            int compactions, int fallbacks, List<String> summaries) {
    }

    /**
     * @param view the profile, {@value #FULL_HISTORY} or {@value #NO_HISTORY}
     */
    public record Answer(String question, int repeat, String view, String answer, Grade grade, String reason) {
    }

    public Optional<Result> result(String name) {
        return results.stream().filter(r -> r.name().equals(name)).findFirst();
    }

    public double recall(String name) {
        return result(name).orElseThrow(() -> new IllegalArgumentException(name + " was not evaluated")).recall();
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder("# Compaction evaluation: ").append(conversation).append("\n\n");
        md.append("Profiles and no-history are graded against the full-history answer; full-history against the"
                + " expected facts.\n\n");
        md.append("| View | Recall | Prompt tokens | Savings | Final view tokens | Compactions | Fallbacks |\n");
        md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (Result r : results) {
            md.append("| ").append(r.name()).append(" | ").append(percent(r.recall())).append(" | ")
                    .append(String.format(Locale.ROOT, "%,d", r.promptTokens())).append(" | ")
                    .append(percent(r.savings())).append(" | ")
                    .append(String.format(Locale.ROOT, "%,d", r.viewTokens())).append(" | ")
                    .append(r.compactions()).append(" | ").append(r.fallbacks()).append(" |\n");
        }

        List<String> views = results.stream().map(Result::name).toList();
        Map<String, Map<String, StringBuilder>> grid = new LinkedHashMap<>();
        for (Answer a : answers) {
            grid.computeIfAbsent(a.question(), q -> new LinkedHashMap<>())
                    .computeIfAbsent(a.view(), v -> new StringBuilder()).append(switch (a.grade()) {
                        case CORRECT -> "✓";
                        case PARTIAL -> "~";
                        case WRONG -> "✗";
                    });
        }
        md.append("\n## Questions\n\n✓ correct, ~ partial, ✗ wrong (one mark per repeat)\n\n| Question |");
        views.forEach(v -> md.append(' ').append(v).append(" |"));
        md.append("\n| --- |").append(" :---: |".repeat(views.size())).append('\n');
        grid.forEach((question, byView) -> {
            md.append("| ").append(escape(question)).append(" |");
            views.forEach(v -> md.append(' ').append(byView.getOrDefault(v, new StringBuilder())).append(" |"));
            md.append('\n');
        });

        // to tell a summary that lost a fact from a model that ignored it
        for (Result r : results) {
            if (!r.summaries().isEmpty()) {
                md.append("\n## Summaries kept by ").append(r.name()).append("\n\n");
                r.summaries().forEach(s -> md.append("```\n").append(s.strip()).append("\n```\n"));
            }
        }

        md.append("\n## Misses\n\n");
        List<Answer> misses = new ArrayList<>(answers.stream().filter(a -> a.grade() != Grade.CORRECT).toList());
        misses.forEach(a -> md.append("- **").append(a.view()).append("** (repeat ").append(a.repeat() + 1)
                .append("), ").append(escape(a.question())).append(" → ").append(a.grade()).append(": ")
                .append(escape(abbreviate(a.answer()))).append(" (").append(escape(a.reason())).append(")\n"));
        if (misses.isEmpty()) {
            md.append("None.\n");
        }
        return md.toString();
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static String abbreviate(String text) {
        String oneLine = text == null ? "" : text.replace('\n', ' ');
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("|", "\\|");
    }
}
