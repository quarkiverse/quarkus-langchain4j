package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The file an evaluation run leaves under the build output: the full report, the id and run that produced it, the
 * baseline file it would replace, and its {@link Comparison} with the baseline.
 * <p>
 * This class owns the field names, so that the writer and every reader agree on the layout. An absent optional
 * value is omitted from the JSON.
 * </p>
 */
public final class PendingFile {

    public static final String ID = "id";
    public static final String RUN = "run";
    public static final String BASELINE_FILE = "baselineFile";
    public static final String COMPARISON = "comparison";
    public static final String REPORT = "report";

    public static final String STATUS = "status";
    public static final String TOLERANCE = "tolerance";
    public static final String MEAN_DIFFERENCE = "meanDifference";
    public static final String STANDARD_ERROR = "standardError";
    public static final String CONFIDENCE_LOW = "confidenceLow";
    public static final String CONFIDENCE_HIGH = "confidenceHigh";
    public static final String SAMPLES = "samples";
    public static final String ADDED = "added";
    public static final String REMOVED = "removed";
    public static final String INCOMPATIBILITY = "incompatibility";
    public static final String ADVISORY_DIFFERENCE = "advisoryDifference";

    public static final String SAMPLE_NAME = "name";
    public static final String SAMPLE_BASELINE = "baseline";
    public static final String SAMPLE_RECEIVED = "received";
    public static final String SAMPLE_ADVISORY_BASELINE = "advisoryBaseline";
    public static final String SAMPLE_ADVISORY_RECEIVED = "advisoryReceived";

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private PendingFile() {
    }

    /**
     * Write a pending file, creating the parent directory when needed.
     *
     * @param file where to write
     * @param id the evaluation id
     * @param run identifies the test JVM run that produced it
     * @param baselineFile the baseline file this result would replace, relative to the test working directory
     * @param reportJson the full JSON report
     * @param comparison the comparison with the baseline
     */
    public static void write(Path file, String id, String run, String baselineFile, String reportJson,
            Comparison comparison) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put(ID, id);
            root.put(RUN, run);
            root.put(BASELINE_FILE, baselineFile);
            root.set(COMPARISON, writeComparison(comparison));
            root.set(REPORT, MAPPER.readTree(reportJson));
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            // written next to the file and moved over it, so an interrupted run never leaves half a file behind
            Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, MAPPER.writeValueAsString(root));
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write pending evaluation result to " + file, e);
        }
    }

    /**
     * Read the run that produced a pending file.
     *
     * @param file the pending file
     * @return the run, or empty if the file does not exist or has none
     */
    public static Optional<String> runOf(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            JsonNode run = MAPPER.readTree(Files.readString(file)).path(RUN);
            return run.isString() ? Optional.of(run.asString()) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read pending evaluation result " + file, e);
        }
    }

    /**
     * Read a comparison written by {@link #write}.
     *
     * @param node the value of the {@link #COMPARISON} field
     * @return the comparison
     */
    public static Comparison readComparison(JsonNode node) {
        List<SampleChange> samples = new ArrayList<>();
        for (JsonNode sample : node.path(SAMPLES)) {
            samples.add(new SampleChange(sample.path(SAMPLE_NAME).asString(), optionalDouble(sample, SAMPLE_BASELINE),
                    optionalDouble(sample, SAMPLE_RECEIVED), optionalDouble(sample, SAMPLE_ADVISORY_BASELINE),
                    optionalDouble(sample, SAMPLE_ADVISORY_RECEIVED)));
        }
        return new Comparison(
                ResultStatus.valueOf(node.path(STATUS).asString().toUpperCase(Locale.ROOT)),
                node.path(TOLERANCE).asDouble(),
                optionalDouble(node, MEAN_DIFFERENCE),
                optionalDouble(node, STANDARD_ERROR),
                optionalDouble(node, CONFIDENCE_LOW),
                optionalDouble(node, CONFIDENCE_HIGH),
                samples,
                strings(node.path(ADDED)),
                strings(node.path(REMOVED)),
                node.hasNonNull(INCOMPATIBILITY) ? Optional.of(node.get(INCOMPATIBILITY).asString()) : Optional.empty(),
                optionalDouble(node, ADVISORY_DIFFERENCE));
    }

    /**
     * Write a comparison in the layout {@link #readComparison} reads.
     *
     * @param comparison the comparison
     * @return the JSON object
     */
    public static ObjectNode writeComparison(Comparison comparison) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(STATUS, comparison.status().name().toLowerCase(Locale.ROOT));
        node.put(TOLERANCE, comparison.tolerance());
        comparison.meanDifference().ifPresent(v -> node.put(MEAN_DIFFERENCE, v));
        comparison.standardError().ifPresent(v -> node.put(STANDARD_ERROR, v));
        comparison.confidenceLow().ifPresent(v -> node.put(CONFIDENCE_LOW, v));
        comparison.confidenceHigh().ifPresent(v -> node.put(CONFIDENCE_HIGH, v));
        comparison.advisoryDifference().ifPresent(v -> node.put(ADVISORY_DIFFERENCE, v));
        comparison.incompatibility().ifPresent(v -> node.put(INCOMPATIBILITY, v));
        ArrayNode samples = node.putArray(SAMPLES);
        for (SampleChange sample : comparison.samples()) {
            ObjectNode s = samples.addObject();
            s.put(SAMPLE_NAME, sample.name());
            sample.baseline().ifPresent(v -> s.put(SAMPLE_BASELINE, v));
            sample.received().ifPresent(v -> s.put(SAMPLE_RECEIVED, v));
            sample.advisoryBaseline().ifPresent(v -> s.put(SAMPLE_ADVISORY_BASELINE, v));
            sample.advisoryReceived().ifPresent(v -> s.put(SAMPLE_ADVISORY_RECEIVED, v));
        }
        ArrayNode added = node.putArray(ADDED);
        comparison.added().forEach(added::add);
        ArrayNode removed = node.putArray(REMOVED);
        comparison.removed().forEach(removed::add);
        return node;
    }

    private static OptionalDouble optionalDouble(JsonNode node, String field) {
        return node.hasNonNull(field) ? OptionalDouble.of(node.get(field).asDouble()) : OptionalDouble.empty();
    }

    private static List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(n -> result.add(n.asString()));
        return result;
    }
}
