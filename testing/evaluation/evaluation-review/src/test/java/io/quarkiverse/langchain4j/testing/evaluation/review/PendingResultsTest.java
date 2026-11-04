package io.quarkiverse.langchain4j.testing.evaluation.review;

import static io.quarkiverse.langchain4j.testing.evaluation.review.ReviewTestSupport.REPORT;
import static io.quarkiverse.langchain4j.testing.evaluation.review.ReviewTestSupport.comparison;
import static io.quarkiverse.langchain4j.testing.evaluation.review.ReviewTestSupport.pending;
import static io.quarkiverse.langchain4j.testing.evaluation.review.ReviewTestSupport.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingResultsTest {

    @TempDir
    Path root;

    private static Comparison changed() {
        return comparison(ResultStatus.CHANGED, List.of(new SampleChange("A", 50, 100)), List.of(), List.of());
    }

    @Test
    void scanFindsPendingResultsInSingleModule() {
        pending(root, "target", "com.acme.FaqTest/faq", REPORT, changed());

        List<PendingResult> results = PendingResults.scan(root);

        assertThat(results).hasSize(1);
        PendingResult result = results.get(0);
        assertThat(result.id()).isEqualTo("com.acme.FaqTest/faq");
        assertThat(result.moduleDir()).isEqualTo(root.toAbsolutePath().normalize());
        assertThat(result.status()).isEqualTo(ResultStatus.CHANGED);
        assertThat(result.comparison()).isEqualTo(changed());
        assertThat(result.baselineFile()).isEqualTo(root.toAbsolutePath().normalize()
                .resolve("src/test/resources/evaluation-baselines/com.acme.FaqTest/faq.json"));
        assertThat(result.summary().samples()).extracting(BaselineSummary.Sample::name).containsExactly("A", "B");
        assertThat(result.details()).hasSize(2);
        PendingResult.SampleDetail a = result.details().get(0);
        assertThat(a.name()).isEqualTo("A");
        assertThat(a.expectedOutput()).isEqualTo("yes");
        assertThat(a.responses()).containsExactly(new PendingResult.Response("yep", true, false),
                new PendingResult.Response("nope", false, true));
    }

    @Test
    void scanFindsPendingResultsInTwoModulesOfMultiModuleRoot() {
        pending(root.resolve("b"), "target", "t.B/two", REPORT, changed());
        pending(root.resolve("a"), "target", "t.A/one", REPORT, changed());
        pending(root.resolve("a"), "target", "t.A/another", REPORT, changed());

        List<PendingResult> results = PendingResults.scan(root);

        assertThat(results).extracting(PendingResult::id).containsExactly("t.A/another", "t.A/one", "t.B/two");
        assertThat(results).extracting(r -> r.moduleDir().getFileName().toString()).containsExactly("a", "a", "b");
    }

    @Test
    void scanFindsGradleBuildDirectory() {
        pending(root, "build", "t.A/one", REPORT, changed());

        assertThat(PendingResults.scan(root)).extracting(PendingResult::id).containsExactly("t.A/one");
    }

    @Test
    void scanSkipsGitAndNodeModules() throws Exception {
        touch(root.resolve(".git/x/target/evaluation-baselines/t.A/one.json"));
        touch(root.resolve("node_modules/x/target/evaluation-baselines/t.A/one.json"));
        touch(root.resolve("target/other/target/evaluation-baselines/t.A/one.json"));

        assertThat(PendingResults.scan(root)).isEmpty();
    }

    @Test
    void scanSkipsUnreadableDirectoryAndFindsTheOthers() {
        pending(root.resolve("a"), "target", "t.A/one", REPORT, changed());
        Path unreadable = root.resolve("b");
        pending(unreadable, "target", "t.B/two", REPORT, changed());
        try {
            unreadable.toFile().setReadable(false);
            assumeFalse(Files.isReadable(unreadable), "the directory is still readable, for instance as root");
            List<String> skipped = new ArrayList<>();

            List<PendingResult> results = PendingResults.scan(root, skipped::add);

            assertThat(results).extracting(PendingResult::id).containsExactly("t.A/one");
            assertThat(skipped).singleElement().asString().startsWith(unreadable.toAbsolutePath().normalize() + ": ");
        } finally {
            unreadable.toFile().setReadable(true);
        }
    }

    @Test
    void scanSkipsMalformedAndNonPendingFilesWithAWarning() throws Exception {
        pending(root, "target", "t.A/one", REPORT, changed());
        Path pendingDir = root.resolve("target/evaluation-baselines/t.A").toAbsolutePath().normalize();
        Files.writeString(pendingDir.resolve("bad.json"), "{ not json");
        Files.writeString(pendingDir.resolve("report.json"), REPORT);
        List<String> skipped = new ArrayList<>();

        List<PendingResult> results = PendingResults.scan(root, skipped::add);

        assertThat(results).extracting(PendingResult::id).containsExactly("t.A/one");
        assertThat(skipped).hasSize(2)
                .anySatisfy(w -> assertThat(w).startsWith(pendingDir.resolve("bad.json") + ": "))
                .anySatisfy(w -> assertThat(w)
                        .isEqualTo(pendingDir.resolve("report.json") + ": not a pending evaluation result"));
    }

    @Test
    void scanFromRootNamedBuildFindsItsPendingResults() {
        Path module = root.resolve("build");
        pending(module, "target", "t.A/one", REPORT, changed());

        assertThat(PendingResults.scan(module)).extracting(PendingResult::id).containsExactly("t.A/one");
    }

    @Test
    void scanWalksIntoModuleDirectoryNamedTarget() throws Exception {
        Path module = root.resolve("target");
        touch(module.resolve("pom.xml"));
        pending(module, "target", "t.A/one", REPORT, changed());

        assertThat(PendingResults.scan(root)).extracting(PendingResult::id).containsExactly("t.A/one");
    }

    @Test
    void readRejectsBaselineFileOutsideTheModule() {
        Path file = root.resolve("target/evaluation-baselines/t.A/one.json");
        PendingFile.write(file, "t.A/one", "run", "../outside.json", REPORT, changed());
        List<String> skipped = new ArrayList<>();

        assertThat(PendingResults.scan(root, skipped::add)).isEmpty();
        assertThat(skipped).singleElement().asString().contains("outside");
        assertThatThrownBy(() -> PendingResults.read(file)).isInstanceOf(IllegalArgumentException.class);
    }
}
