package io.quarkiverse.langchain4j.testing.scorer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Report of the evaluation of a set of samples.
 */
public class EvaluationReport {

    private final List<Scorer.EvaluationResult<?>> evaluations;
    private final double score;

    /**
     * Create a new evaluation report and computes the global score.
     *
     * @param evaluations the evaluations, must not be {@code null}, must not be empty.
     */
    public EvaluationReport(List<Scorer.EvaluationResult<?>> evaluations) {
        this.evaluations = evaluations;
        this.score = 100.0 * evaluations.stream().filter(Scorer.EvaluationResult::passed).count() / evaluations.size();
    }

    /**
     * @return the global score, between 0.0 and 100.0.
     */
    public double score() {
        return score;
    }

    /**
     * @return the evaluations
     */
    public List<Scorer.EvaluationResult<?>> evaluations() {
        return evaluations;
    }

    /**
     * Compute the score for a given tag.
     *
     * @param tag the tag, must not be {@code null}
     * @return the score for the given tag, between 0.0 and 100.0.
     */
    public double scoreForTag(String tag) {
        return 100.0 * evaluations.stream().filter(e -> e.sample().tags().contains(tag))
                .filter(Scorer.EvaluationResult::passed).count()
                / evaluations.stream().filter(e -> e.sample().tags().contains(tag)).count();
    }

    /**
     * Write the report to a file using the Markdown syntax.
     *
     * @param output the output file, must not be {@code null}
     * @throws IOException if an error occurs while writing the report
     */
    public void writeReport(File output) throws IOException {
        writeReport(output, false);
    }

    /**
     * Write the report to a file using the Markdown syntax.
     *
     * @param output the output file, must not be {@code null}
     * @param includeResult whether to include the expectedOutput and result of the evaluation in the report
     * @throws IOException if an error occurs while writing the report
     */
    public void writeReport(File output, boolean includeResult) throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("# Evaluation Report\n\n");
        buffer.append("**Global Score**: ").append(score).append("\n\n");

        List<String> tags = evaluations.stream().flatMap(e -> e.sample().tags().stream()).distinct().toList();
        if (!tags.isEmpty()) {
            buffer.append("## Score per tags\n\n");
            for (String tag : tags) {
                buffer.append("- **").append(tag).append("**: ").append(scoreForTag(tag)).append("\n");
            }
        }

        buffer.append("\n## Details\n\n");
        var detailHeader = includeResult ? "### " : "- ";
        for (Scorer.EvaluationResult<?> evaluation : evaluations) {
            buffer.append(detailHeader).append(evaluation.sample().name()).append(": ")
                    .append(evaluation.passed() ? "PASSED" : "FAILED").append("\n");
            if (includeResult) {
                buffer.append("#### Result\n").append(evaluation.result()).append("\n");
                buffer.append("#### Expected Output\n").append(evaluation.sample().expectedOutput()).append("\n");
            }
        }

        Files.write(output.toPath(), buffer.toString().getBytes());
    }

}
