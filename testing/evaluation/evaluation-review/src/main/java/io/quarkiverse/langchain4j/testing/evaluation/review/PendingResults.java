package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Finds and reads the pending results an evaluation run leaves under the build output.
 */
public final class PendingResults {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SKIPPED = Set.of(".git", "node_modules");
    private static final Set<String> BUILD_DIRS = Set.of("target", "build");
    private static final String PENDING_DIR = "evaluation-baselines";

    private PendingResults() {
    }

    /**
     * Find every pending result below a root, in any module, ignoring what cannot be read.
     *
     * @param root the directory to search
     * @return the pending results, sorted by module, then id
     */
    public static List<PendingResult> scan(Path root) {
        return scan(root, skipped -> {
        });
    }

    /**
     * Find every pending result below a root, in any module. Directories that cannot be read and files that are not
     * readable pending results are skipped.
     *
     * @param root the directory to search
     * @param skipped receives one {@code <path>: <reason>} line per skipped directory or file
     * @return the pending results, sorted by module, then id
     */
    public static List<PendingResult> scan(Path root, Consumer<String> skipped) {
        Path start = root.toAbsolutePath().normalize();
        List<Path> pendingDirs = new ArrayList<>();
        walk(start, new SkippingVisitor(skipped) {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(start) || isModule(dir)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = String.valueOf(dir.getFileName());
                if (SKIPPED.contains(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (BUILD_DIRS.contains(name)) {
                    Path pending = dir.resolve(PENDING_DIR);
                    if (Files.isDirectory(pending)) {
                        pendingDirs.add(pending);
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        List<Path> files = new ArrayList<>();
        for (Path pendingDir : pendingDirs) {
            walk(pendingDir, new SkippingVisitor(skipped) {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".json")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        List<PendingResult> results = new ArrayList<>();
        for (Path file : files) {
            try {
                results.add(read(file));
            } catch (RuntimeException e) {
                skipped.accept(file + ": " + reason(e));
            }
        }
        results.sort(Comparator.comparing((PendingResult r) -> r.moduleDir().toString()).thenComparing(PendingResult::id));
        return results;
    }

    private static boolean isModule(Path dir) {
        return Files.exists(dir.resolve("pom.xml")) || Files.exists(dir.resolve("build.gradle"))
                || Files.exists(dir.resolve("build.gradle.kts"));
    }

    private static void walk(Path start, SkippingVisitor visitor) {
        try {
            Files.walkFileTree(start, visitor);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan " + start + " for pending evaluation results", e);
        }
    }

    private static String reason(Throwable e) {
        Throwable cause = e instanceof UncheckedIOException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof JacksonException json) {
            return json.getOriginalMessage();
        }
        if (cause instanceof AccessDeniedException) {
            return "permission denied";
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
    }

    /**
     * Reports directories and files that cannot be read instead of failing the scan.
     */
    private abstract static class SkippingVisitor extends SimpleFileVisitor<Path> {

        private final Consumer<String> skipped;

        SkippingVisitor(Consumer<String> skipped) {
            this.skipped = skipped;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
            // called for a directory that cannot be opened too, whose subtree is then skipped
            skipped.accept(file + ": " + reason(e));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException e) {
            if (e != null) {
                skipped.accept(dir + ": " + reason(e));
            }
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Read one pending file. The module directory is derived from the file location and the id, because the file is
     * <code>moduleDir/&lt;build dir&gt;/evaluation-baselines/&lt;id&gt;.json</code> and the id may contain slashes.
     *
     * @param pendingFile the pending file
     * @return the pending result
     * @throws UncheckedIOException when the file cannot be read
     * @throws JacksonException when the file is not valid JSON
     * @throws IllegalArgumentException when the file is not a pending result, or its baseline file is outside its
     *         module
     */
    public static PendingResult read(Path pendingFile) {
        Path file = pendingFile.toAbsolutePath().normalize();
        JsonNode root;
        try {
            root = MAPPER.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read pending evaluation result " + file, e);
        }
        if (!root.path(PendingFile.ID).isString() || !root.path(PendingFile.BASELINE_FILE).isString()
                || !root.path(PendingFile.COMPARISON).isObject() || !root.path(PendingFile.REPORT).isObject()) {
            throw new IllegalArgumentException("not a pending evaluation result");
        }
        String id = root.path(PendingFile.ID).asString();
        // from the file's directory up to evaluation-baselines, then the build dir, then the module
        Path moduleDir = file.getParent();
        for (int i = 0; i < id.split("/").length - 1 + 2; i++) {
            moduleDir = moduleDir.getParent();
        }
        String baseline = root.path(PendingFile.BASELINE_FILE).asString();
        Path baselineFile = moduleDir.resolve(baseline).normalize();
        if (!baselineFile.startsWith(moduleDir)) {
            throw new IllegalArgumentException("its baseline file " + baseline + " is outside the module " + moduleDir);
        }
        Comparison comparison = PendingFile.readComparison(root.path(PendingFile.COMPARISON));
        JsonNode report = root.path(PendingFile.REPORT);
        return new PendingResult(file, moduleDir, id, comparison.status(), comparison, baselineFile, details(report),
                BaselineSummary.fromReport(report));
    }

    private static List<PendingResult.SampleDetail> details(JsonNode report) {
        Map<String, String> expected = new LinkedHashMap<>();
        Map<String, List<PendingResult.Response>> responses = new LinkedHashMap<>();
        for (JsonNode evaluation : report.path("evaluations")) {
            String name = evaluation.path("name").asString();
            expected.computeIfAbsent(name,
                    n -> evaluation.hasNonNull("expectedOutput") ? evaluation.get("expectedOutput").asString() : null);
            responses.computeIfAbsent(name, n -> new ArrayList<>()).add(new PendingResult.Response(
                    text(evaluation, "actualOutput"), evaluation.path("passed").asBoolean(),
                    evaluation.path("advisory").asBoolean(), evaluation.path("trial").asInt(0),
                    text(evaluation, "strategy"), text(evaluation, "explanation"), text(evaluation, "error")));
        }
        List<PendingResult.SampleDetail> details = new ArrayList<>();
        responses.forEach((name, r) -> details.add(new PendingResult.SampleDetail(name, expected.get(name), r)));
        return details;
    }

    private static String text(JsonNode evaluation, String field) {
        return evaluation.hasNonNull(field) ? evaluation.get(field).asString() : null;
    }
}
