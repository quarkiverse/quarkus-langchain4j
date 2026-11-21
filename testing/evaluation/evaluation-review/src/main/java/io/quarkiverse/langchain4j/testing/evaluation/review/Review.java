package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Accepts or rejects pending evaluation results, and describes them as JSON.
 */
public final class Review {

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private Review() {
    }

    /**
     * Write the summary of a pending result as the committed baseline, then delete the pending file.
     *
     * @param result the result to accept
     */
    public static void accept(PendingResult result) {
        result.summary().write(result.baselineFile());
        delete(result);
    }

    /**
     * Delete a pending file, leaving the baseline as it is.
     *
     * @param result the result to reject
     */
    public static void reject(PendingResult result) {
        delete(result);
    }

    /**
     * Describe pending results as JSON, for tools and agents.
     *
     * @param root the directory that was scanned; module and baseline file paths are relative to it
     * @param results the results to describe
     * @return the JSON text
     */
    public static String toJson(Path root, List<PendingResult> results) {
        Path base = root.toAbsolutePath().normalize();
        ObjectNode json = MAPPER.createObjectNode();
        ArrayNode evaluations = json.putArray("evaluations");
        for (PendingResult result : results) {
            ObjectNode evaluation = evaluations.addObject();
            evaluation.put(PendingFile.ID, result.id());
            evaluation.put("module", base.relativize(result.moduleDir()).toString());
            evaluation.put(PendingFile.STATUS, result.status().name().toLowerCase(Locale.ROOT));
            evaluation.put(PendingFile.BASELINE_FILE, base.relativize(result.baselineFile()).toString());
            evaluation.set(PendingFile.COMPARISON, PendingFile.writeComparison(result.comparison()));
            evaluation.put("errors", errors(result).size());
            samples(evaluation.putArray(PendingFile.SAMPLES), result);
        }
        return MAPPER.writeValueAsString(json);
    }

    private static void samples(ArrayNode samples, PendingResult result) {
        Map<String, PendingResult.SampleDetail> details = result.details().stream()
                .collect(Collectors.toMap(PendingResult.SampleDetail::name, Function.identity()));
        for (Row row : rows(result)) {
            SampleChange scores = row.scores();
            ObjectNode node = samples.addObject();
            node.put(PendingFile.SAMPLE_NAME, scores.name());
            scores.baseline().ifPresent(v -> node.put(PendingFile.SAMPLE_BASELINE, v));
            scores.received().ifPresent(v -> node.put(PendingFile.SAMPLE_RECEIVED, v));
            scores.advisoryBaseline().ifPresent(v -> node.put(PendingFile.SAMPLE_ADVISORY_BASELINE, v));
            scores.advisoryReceived().ifPresent(v -> node.put(PendingFile.SAMPLE_ADVISORY_RECEIVED, v));
            PendingResult.SampleDetail detail = details.get(scores.name());
            if (detail != null && detail.expectedOutput() != null) {
                node.put("expected", detail.expectedOutput());
            }
            ArrayNode responses = node.putArray("responses");
            if (detail != null) {
                for (PendingResult.Response response : detail.responses()) {
                    responses.add(response(response));
                }
            }
        }
    }

    private static ObjectNode response(PendingResult.Response response) {
        ObjectNode r = MAPPER.createObjectNode();
        r.put("output", response.output());
        if (response.trial() > 0) {
            r.put("trial", response.trial());
        }
        if (response.strategy() != null) {
            r.put("strategy", response.strategy());
        }
        r.put("passed", response.passed());
        r.put("advisory", response.advisory());
        if (response.explanation() != null) {
            r.put("explanation", response.explanation());
        }
        if (response.error() != null) {
            r.put("error", response.error());
        }
        return r;
    }

    /**
     * Describe evaluations that ended in an error rather than in a verdict, such as a model provider refusing the
     * call. Their scores say nothing about the answers, so a run made of them is no baseline.
     *
     * @param errors the error of each evaluation that ended in one
     * @param evaluations the number of evaluations in the run
     * @return the description, with the distinct errors most frequent first, each by its exception type and the
     *         provider's error code and message, or the first line of its message, cut to 100 characters, or empty
     *         when there are none
     */
    public static Optional<String> errorSummary(List<String> errors, int evaluations) {
        if (errors.isEmpty()) {
            return Optional.empty();
        }
        // the first line, cut short, so that errors differing only in a request id or a stack trace count as one
        Map<String, Long> counts = errors.stream()
                .collect(Collectors.groupingBy(Review::gist, LinkedHashMap::new, Collectors.counting()));
        StringBuilder summary = new StringBuilder(String.format(Locale.ROOT,
                "%d of %d evaluations ended in an error, so their scores say nothing about the answers:", errors.size(),
                evaluations));
        counts.entrySet().stream().sorted(Map.Entry.<String, Long> comparingByValue().reversed()).limit(3)
                .forEach(e -> summary.append("\n  - ").append(e.getKey()).append(" (").append(e.getValue()).append(")"));
        if (counts.size() > 3) {
            summary.append("\n  - and ").append(counts.size() - 3).append(" other errors");
        }
        return Optional.of(summary.toString());
    }

    /**
     * What an error is about, short enough to read in a list: the simple name of the exception, then the provider's
     * error code and message when the message is a JSON error body, else its first line, cut to 100 characters. Errors
     * with the same gist are counted as one, so request ids and stack traces do not split them.
     */
    private static String gist(String error) {
        String type = "";
        String message = error.strip();
        int colon = message.indexOf(": ");
        if (colon > 0 && message.substring(0, colon).matches("[\\w$.]+")) {
            String name = message.substring(0, colon);
            type = name.substring(name.lastIndexOf('.') + 1) + ": ";
            message = message.substring(colon + 2).strip();
        }
        String text = message;
        String gist = type + providerError(text).orElseGet(() -> text.lines().map(String::strip)
                .filter(line -> !line.isEmpty()).findFirst().orElse(""));
        return gist.length() > 100 ? gist.substring(0, 100) + "…" : gist;
    }

    private static Optional<String> providerError(String message) {
        if (!message.startsWith("{")) {
            return Optional.empty();
        }
        JsonNode body;
        try {
            body = MAPPER.readTree(message);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        JsonNode text = body.findValue("message");
        if (text == null || !text.isString()) {
            return Optional.empty();
        }
        JsonNode code = body.findValue("code");
        return Optional.of((code != null && code.isString() ? code.asString() + ": " : "") + text.asString());
    }

    /**
     * The errors of the evaluations of a pending result that ended in one.
     *
     * @param result the pending result
     * @return the error of each such evaluation, in report order
     */
    public static List<String> errors(PendingResult result) {
        return result.details().stream().flatMap(d -> d.responses().stream()).map(PendingResult.Response::error)
                .filter(java.util.Objects::nonNull).toList();
    }

    /**
     * The number of evaluations of a pending result.
     *
     * @param result the pending result
     * @return the number of evaluations
     */
    public static int evaluations(PendingResult result) {
        return result.details().stream().mapToInt(d -> d.responses().size()).sum();
    }

    /**
     * How a sample of a pending result relates to the baseline.
     */
    public enum Kind {
        /** In the baseline and in the received run. */
        COMPARED,
        /** Only in the received run. */
        NEW,
        /** Only in the baseline. */
        REMOVED,
        /** In the received run of an incompatible result, which was not compared. */
        RECEIVED
    }

    /**
     * One sample of a pending result, as the review shows it.
     *
     * @param scores the scores; only the received ones for a new sample, only the baseline ones for a removed one
     * @param kind how the sample relates to the baseline
     */
    public record Row(SampleChange scores, Kind kind) {
    }

    /**
     * The samples of a pending result, compared ones first, then new, then removed ones. A removed sample is not in
     * the received report, so its scores come from the committed baseline file, when it can be read. The samples of an
     * incompatible result are its received ones.
     *
     * @param result the pending result
     * @return the rows
     */
    public static List<Row> rows(PendingResult result) {
        Comparison comparison = result.comparison();
        Map<String, BaselineSummary.Sample> received = byName(Optional.of(result.summary()));
        List<Row> rows = new ArrayList<>();
        if (result.status() == ResultStatus.INCOMPATIBLE) {
            received.values().forEach(s -> rows.add(new Row(received(s), Kind.RECEIVED)));
            return rows;
        }
        comparison.samples().forEach(s -> rows.add(new Row(s, Kind.COMPARED)));
        for (String name : comparison.added()) {
            BaselineSummary.Sample sample = received.get(name);
            rows.add(new Row(sample == null ? empty(name) : received(sample), Kind.NEW));
        }
        Map<String, BaselineSummary.Sample> baseline = comparison.removed().isEmpty() ? Map.of()
                : byName(committed(result));
        for (String name : comparison.removed()) {
            BaselineSummary.Sample sample = baseline.get(name);
            SampleChange scores = sample == null ? empty(name)
                    : new SampleChange(name, score(sample.blocking()), OptionalDouble.empty(), score(sample.advisory()),
                            OptionalDouble.empty());
            rows.add(new Row(scores, Kind.REMOVED));
        }
        return rows;
    }

    private static Optional<BaselineSummary> committed(PendingResult result) {
        if (!Files.exists(result.baselineFile())) {
            return Optional.empty();
        }
        try {
            return Optional.of(BaselineSummary.read(result.baselineFile()));
        } catch (RuntimeException e) {
            // an unreadable baseline only costs the scores of the removed samples
            return Optional.empty();
        }
    }

    private static Map<String, BaselineSummary.Sample> byName(Optional<BaselineSummary> summary) {
        Map<String, BaselineSummary.Sample> samples = new LinkedHashMap<>();
        summary.ifPresent(s -> s.samples().forEach(sample -> samples.put(sample.name(), sample)));
        return samples;
    }

    private static SampleChange received(BaselineSummary.Sample sample) {
        return new SampleChange(sample.name(), OptionalDouble.empty(), score(sample.blocking()), OptionalDouble.empty(),
                score(sample.advisory()));
    }

    private static SampleChange empty(String name) {
        return new SampleChange(name, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty());
    }

    private static OptionalDouble score(BaselineSummary.Count count) {
        return count.total() == 0 ? OptionalDouble.empty() : OptionalDouble.of(100.0 * count.passed() / count.total());
    }

    private static void delete(PendingResult result) {
        try {
            Files.deleteIfExists(result.pendingFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + result.pendingFile(), e);
        }
    }
}
