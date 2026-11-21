package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The compact form of an evaluation report that is committed as a baseline: the number of trials, the names of the
 * strategies and, for each sample, how many of its blocking and advisory evaluations passed.
 * <p>
 * A summary is built from a full JSON report with {@link #fromReport(JsonNode)}, and it is stored with
 * {@link #write(Path)} and read back with {@link #read(Path)}. The stored form starts with a {@code formatVersion}
 * field, which {@link #isSummary(JsonNode)} uses to tell it from a full report.
 * </p>
 *
 * @param trials the number of times each sample was executed
 * @param strategies the names of the evaluation strategies, empty when the report does not list them
 * @param samples one entry per sample, in order of first appearance in the report
 */
public record BaselineSummary(int trials, List<String> strategies, List<Sample> samples) {

    static final int FORMAT_VERSION = 1;

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    public BaselineSummary {
        strategies = List.copyOf(strategies);
        samples = List.copyOf(samples);
    }

    /**
     * The results of one sample.
     *
     * @param name the sample name
     * @param tags the sample tags
     * @param blocking the passed and total counts of the evaluations that decide the outcome
     * @param advisory the passed and total counts of the advisory evaluations
     */
    public record Sample(String name, List<String> tags, Count blocking, Count advisory) {

        public Sample {
            tags = List.copyOf(tags);
        }

        /**
         * The score of the sample, as the comparison with a baseline computes it.
         *
         * @return the percentage of its blocking evaluations that passed, 0 when it has none
         */
        public double score() {
            return blocking.total() == 0 ? 0.0 : 100.0 * blocking.passed() / blocking.total();
        }
    }

    /**
     * A number of passed evaluations out of a total.
     *
     * @param passed the number of evaluations that passed
     * @param total the number of evaluations
     */
    public record Count(int passed, int total) {
    }

    /**
     * Builds a summary from a full JSON report, as written by the {@code json} report formatter.
     *
     * @param report the parsed report
     * @return the summary of the report
     */
    public static BaselineSummary fromReport(JsonNode report) {
        List<String> strategies = new ArrayList<>();
        report.path("strategies").forEach(s -> strategies.add(s.asString()));

        Map<String, List<String>> tagsByName = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (JsonNode evaluation : report.path("evaluations")) {
            String name = evaluation.path("name").asString();
            tagsByName.computeIfAbsent(name, n -> {
                List<String> tags = new ArrayList<>();
                evaluation.path("tags").forEach(t -> tags.add(t.asString()));
                return tags;
            });
            // passed and total for blocking, then passed and total for advisory
            int[] c = counts.computeIfAbsent(name, n -> new int[4]);
            int offset = evaluation.path("advisory").asBoolean() ? 2 : 0;
            c[offset + 1]++;
            if (evaluation.path("passed").asBoolean()) {
                c[offset]++;
            }
        }

        List<Sample> samples = new ArrayList<>();
        counts.forEach((name, c) -> samples.add(new Sample(name, tagsByName.get(name),
                new Count(c[0], c[1]), new Count(c[2], c[3]))));
        return new BaselineSummary(report.path("trials").asInt(1), strategies, samples);
    }

    /**
     * Tells whether a parsed file is a summary rather than a full report.
     *
     * @param root the parsed file
     * @return {@code true} when it has a {@code formatVersion} field
     */
    public static boolean isSummary(JsonNode root) {
        return root.has("formatVersion");
    }

    /**
     * Reads a summary from its parsed stored form.
     *
     * @param root the parsed summary
     * @return the summary
     * @throws IllegalArgumentException when a sample has no name or no counts
     */
    public static BaselineSummary read(JsonNode root) {
        if (!root.path("samples").isArray()) {
            throw new IllegalArgumentException("the baseline summary has no samples");
        }
        List<String> strategies = new ArrayList<>();
        root.path("strategies").forEach(s -> strategies.add(s.asString()));
        List<Sample> samples = new ArrayList<>();
        for (JsonNode s : root.path("samples")) {
            if (!s.path("name").isString() || !isCount(s.path("blocking")) || !isCount(s.path("advisory"))) {
                throw new IllegalArgumentException("the baseline summary has a sample without a name or counts");
            }
            List<String> tags = new ArrayList<>();
            s.path("tags").forEach(t -> tags.add(t.asString()));
            samples.add(new Sample(s.path("name").asString(), tags, count(s.path("blocking")), count(s.path("advisory"))));
        }
        return new BaselineSummary(root.path("trials").asInt(1), strategies, samples);
    }

    /**
     * Reads a baseline file, either a summary written by {@link #write(Path)} or a full JSON report, which is then
     * summarised.
     *
     * @param file the baseline file
     * @return the summary
     * @throws UncheckedIOException when the file cannot be read
     * @throws tools.jackson.core.JacksonException when the file is not valid JSON
     * @throws IllegalArgumentException when the file is neither, or a sample or evaluation misses required fields
     */
    public static BaselineSummary read(Path file) {
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the baseline " + file, e);
        }
        JsonNode root = MAPPER.readTree(json);
        if (isSummary(root)) {
            return read(root);
        }
        if (!root.path("evaluations").isArray()) {
            throw new IllegalArgumentException(file + " is neither a baseline summary nor an evaluation report");
        }
        for (JsonNode evaluation : root.path("evaluations")) {
            if (!evaluation.path("name").isString() || !evaluation.path("passed").isBoolean()) {
                throw new IllegalArgumentException(file + " has an evaluation without a name or a passed flag");
            }
        }
        return fromReport(root);
    }

    /**
     * Writes this summary as pretty-printed JSON, creating the parent directories when needed.
     *
     * @param file the file to write
     * @throws UncheckedIOException when the file cannot be written
     */
    public void write(Path file) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("trials", trials);
        ArrayNode strategyNodes = root.putArray("strategies");
        strategies.forEach(strategyNodes::add);
        ArrayNode sampleNodes = root.putArray("samples");
        for (Sample sample : samples) {
            ObjectNode node = sampleNodes.addObject();
            node.put("name", sample.name());
            ArrayNode tagNodes = node.putArray("tags");
            sample.tags().forEach(tagNodes::add);
            put(node, "blocking", sample.blocking());
            put(node, "advisory", sample.advisory());
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, MAPPER.writeValueAsString(root) + System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the baseline " + file, e);
        }
    }

    private static void put(ObjectNode node, String field, Count count) {
        ObjectNode c = node.putObject(field);
        c.put("passed", count.passed());
        c.put("total", count.total());
    }

    private static boolean isCount(JsonNode node) {
        return node.path("passed").isInt() && node.path("total").isInt();
    }

    private static Count count(JsonNode node) {
        return new Count(node.path("passed").asInt(), node.path("total").asInt());
    }
}
