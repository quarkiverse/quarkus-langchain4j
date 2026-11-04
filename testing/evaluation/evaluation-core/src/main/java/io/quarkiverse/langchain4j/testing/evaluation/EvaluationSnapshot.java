package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.testing.evaluation.review.BaselineSummary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The part of an {@link EvaluationReport} that a later run can be compared with: the number of trials and, for
 * each evaluation, the sample name, its tags, whether it passed and whether it came from an advisory strategy.
 * <p>
 * A snapshot is what a {@link BaselineStore} keeps between runs. It can be taken from a report with
 * {@link #of(EvaluationReport)} and read back from a report saved in the JSON format with {@link #read(Path)}.
 * </p>
 *
 * @param trials the number of times each sample was executed, at least 1
 * @param strategies the names of the strategies that produced the evaluations, empty when unknown
 * @param records one record per evaluation, in report order
 */
public record EvaluationSnapshot(int trials, List<String> strategies, List<Record> records) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public EvaluationSnapshot {
        if (trials < 1) {
            throw new IllegalArgumentException("Trials must be at least 1, got: " + trials);
        }
        strategies = List.copyOf(strategies);
        records = List.copyOf(records);
    }

    /**
     * One evaluation of one sample by one strategy.
     *
     * @param name the sample name
     * @param tags the sample tags
     * @param passed whether the evaluation passed
     * @param advisory whether the strategy was advisory, see {@link EvaluationStrategy#isAdvisory()}
     */
    public record Record(String name, List<String> tags, boolean passed, boolean advisory) {

        public Record {
            tags = List.copyOf(tags);
        }
    }

    /**
     * Take a snapshot of a report.
     *
     * @param report the report, must not be {@code null}
     * @return the snapshot
     */
    public static EvaluationSnapshot of(EvaluationReport<?> report) {
        List<Record> records = new ArrayList<>();
        for (Scorer.EvaluationResult<?> evaluation : report.evaluations()) {
            records.add(new Record(evaluation.sample().name(), evaluation.sample().tags(), evaluation.passed(),
                    evaluation.advisory()));
        }
        return new EvaluationSnapshot(report.trials(), report.strategies(), records);
    }

    /**
     * Read a snapshot from a report saved in the JSON format, see {@link JsonReportFormatter}, or from a baseline
     * summary, see {@link BaselineSummary}. A summary keeps counts, so each count is expanded into that many passing
     * and failing records, without the order of the original evaluations.
     *
     * @param file the report file, must not be {@code null}
     * @return the snapshot
     * @throws UncheckedIOException if the file cannot be read
     * @throws tools.jackson.core.JacksonException if the file is not valid JSON
     * @throws IllegalArgumentException if the file is neither a summary nor a report, or misses required fields
     */
    public static EvaluationSnapshot read(Path file) {
        JsonNode root;
        try {
            root = MAPPER.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read evaluation report " + file, e);
        }
        if (BaselineSummary.isSummary(root)) {
            return of(BaselineSummary.read(root));
        }
        if (!root.path("evaluations").isArray()) {
            throw new IllegalArgumentException(file + " is neither a baseline summary nor an evaluation report");
        }
        for (JsonNode evaluation : root.path("evaluations")) {
            if (!evaluation.path("name").isString() || !evaluation.path("passed").isBoolean()) {
                throw new IllegalArgumentException(file + " has an evaluation without a name or a passed flag");
            }
        }
        List<String> strategies = new ArrayList<>();
        for (JsonNode strategy : root.path("strategies")) {
            strategies.add(strategy.asString());
        }
        List<Record> records = new ArrayList<>();
        for (JsonNode evaluation : root.path("evaluations")) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : evaluation.path("tags")) {
                tags.add(tag.asString());
            }
            records.add(new Record(evaluation.path("name").asString(), tags,
                    evaluation.path("passed").asBoolean(), evaluation.path("advisory").asBoolean()));
        }
        return new EvaluationSnapshot(root.path("trials").asInt(1), strategies, records);
    }

    private static EvaluationSnapshot of(BaselineSummary summary) {
        List<Record> records = new ArrayList<>();
        for (BaselineSummary.Sample sample : summary.samples()) {
            addRecords(records, sample, sample.blocking(), false);
            addRecords(records, sample, sample.advisory(), true);
        }
        return new EvaluationSnapshot(summary.trials(), summary.strategies(), records);
    }

    private static void addRecords(List<Record> records, BaselineSummary.Sample sample, BaselineSummary.Count count,
            boolean advisory) {
        for (int i = 0; i < count.total(); i++) {
            records.add(new Record(sample.name(), sample.tags(), i < count.passed(), advisory));
        }
    }

    /**
     * The names of all samples, with blocking or advisory evaluations, in report order.
     *
     * @return the sample names
     */
    public List<String> sampleNames() {
        return records.stream().map(Record::name).distinct().toList();
    }

    /**
     * The score of each sample over its blocking evaluations, in report order.
     *
     * @return the percentage of passed blocking evaluations per sample name, between 0.0 and 100.0
     */
    public Map<String, Double> sampleScores() {
        return sampleScores(false);
    }

    /**
     * The score of each sample over its advisory evaluations, in report order.
     *
     * @return the percentage of passed advisory evaluations per sample name, between 0.0 and 100.0
     */
    public Map<String, Double> advisorySampleScores() {
        return sampleScores(true);
    }

    private Map<String, Double> sampleScores(boolean advisory) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (Record record : records) {
            if (record.advisory() == advisory) {
                int[] count = counts.computeIfAbsent(record.name(), n -> new int[2]);
                count[0] += record.passed() ? 1 : 0;
                count[1]++;
            }
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        counts.forEach((name, count) -> scores.put(name, 100.0 * count[0] / count[1]));
        return scores;
    }
}
