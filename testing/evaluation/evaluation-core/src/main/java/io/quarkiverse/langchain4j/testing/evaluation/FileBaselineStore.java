package io.quarkiverse.langchain4j.testing.evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.testing.evaluation.review.BaselineSummary;
import io.quarkiverse.langchain4j.testing.evaluation.review.Comparison;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A {@link BaselineStore} keeping one {@link BaselineSummary} per evaluation id in a directory, as {@code <id>.json}.
 * <p>
 * The directory is meant to be committed with the tests, so that a change of baseline shows up in the diff of
 * the change that caused it. The store never writes there by itself: the result of each run goes to a pending
 * directory under the build output, as a {@link PendingFile}, and accepting it as the baseline is something the
 * developer does on purpose, with {@code quarkus langchain4j-eval review} or by extracting the report from the
 * pending file into the baseline file.
 * </p>
 */
public class FileBaselineStore implements BaselineStore {

    private static final Logger LOG = Logger.getLogger(FileBaselineStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REVIEW_HINT = "Run `quarkus langchain4j-eval review` to accept or reject it.\n"
            + "Manual review (no CLI): https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html#reviewing-results-manually";

    private final Path directory;
    private final Path pendingDirectory;

    /**
     * @param directory the directory holding the baselines, meant to be committed
     */
    public FileBaselineStore(Path directory) {
        this(directory, Path.of("target", "evaluation-baselines"));
    }

    /**
     * @param directory the directory holding the baselines, meant to be committed
     * @param pendingDirectory the directory where the result of each run is written, created when needed
     */
    public FileBaselineStore(Path directory, Path pendingDirectory) {
        this.directory = directory;
        this.pendingDirectory = pendingDirectory;
    }

    /**
     * The store used by default: baselines in {@code src/test/resources/evaluation-baselines}, pending results in
     * {@code evaluation-baselines} under the build directory, which is {@code build} when the working directory
     * has a Gradle build file and {@code target} otherwise.
     *
     * @return the store, relative to the working directory
     */
    public static FileBaselineStore conventional() {
        return conventional(Path.of(""));
    }

    static FileBaselineStore conventional(Path root) {
        boolean gradle = Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"));
        return new FileBaselineStore(root.resolve("src/test/resources/evaluation-baselines"),
                root.resolve(gradle ? "build" : "target").resolve("evaluation-baselines"));
    }

    /**
     * The system property identifying the current test run. The {@code evaluation-junit5} module sets it to a new
     * value each time the JUnit Platform starts executing tests, so that a second execution in the same JVM, such as
     * Quarkus continuous testing or a Surefire rerun, is a new run. A system property is shared across class loaders,
     * which a static field under {@code @QuarkusTest} would not be. {@code quarkus-langchain4j-core} declares it, so
     * Quarkus does not warn about it as an unrecognized property.
     */
    public static final String RUN_PROPERTY = "quarkus.langchain4j.evaluation.run";

    /**
     * Identifies the current test run, so that the same id used twice in it can be told from a leftover of an
     * earlier run.
     *
     * @return the value of {@link #RUN_PROPERTY} when it is set, else the process id and start time
     */
    static String currentRun() {
        String run = System.getProperty(RUN_PROPERTY);
        if (run != null) {
            return run;
        }
        ProcessHandle process = ProcessHandle.current();
        return process.info().startInstant().map(start -> process.pid() + "-" + start)
                .orElseGet(() -> String.valueOf(process.pid()));
    }

    /**
     * @param id the evaluation id
     * @return the file holding the baseline of that evaluation, whether it exists or not
     */
    public Path fileOf(String id) {
        return directory.resolve(id + ".json");
    }

    /**
     * @param id the evaluation id
     * @return the file holding the pending result of the last run of that evaluation, whether it exists or not
     */
    public Path pendingFileOf(String id) {
        return pendingDirectory.resolve(id + ".json");
    }

    @Override
    public Optional<EvaluationSnapshot> load(String id) {
        Path file = fileOf(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(EvaluationSnapshot.read(file));
    }

    /**
     * Write the report as a {@link BaselineSummary}, the form {@code quarkus langchain4j-eval accept} writes.
     */
    @Override
    public void save(String id, EvaluationReport<?> report) {
        JsonNode json = MAPPER.readTree(new JsonReportFormatter().format(report, Map.of()));
        BaselineSummary.fromReport(json).write(fileOf(id));
    }

    @Override
    public String receive(String id, EvaluationReport<?> report, Comparison comparison) {
        Path file = pendingFileOf(id);
        String run = currentRun();
        Optional<String> previousRun;
        try {
            previousRun = PendingFile.runOf(file);
        } catch (JacksonException e) {
            // for instance cut short by an earlier run that was killed; this run's result replaces it
            LOG.warnf("Replacing %s, which is not valid JSON: %s", file, e.getOriginalMessage());
            previousRun = Optional.empty();
        }
        if (previousRun.filter(run::equals).isPresent()) {
            throw new IllegalStateException(id + " was already compared with its baseline in this run. "
                    + "Pass an explicit id to isNotWorseThanBaseline(String) for each evaluation.");
        }
        PendingFile.write(file, id, run, fileOf(id).toString(), new JsonReportFormatter().format(report, Map.of()),
                comparison);
        return "Pending result: " + file + "\nBaseline: " + fileOf(id) + "\n" + REVIEW_HINT;
    }

    @Override
    public String toString() {
        return "FileBaselineStore(" + directory + ")";
    }
}
