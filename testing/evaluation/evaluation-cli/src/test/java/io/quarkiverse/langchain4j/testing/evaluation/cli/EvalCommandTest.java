package io.quarkiverse.langchain4j.testing.evaluation.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.langchain4j.testing.evaluation.review.Comparison;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingFile;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResults;
import io.quarkiverse.langchain4j.testing.evaluation.review.ResultStatus;
import io.quarkiverse.langchain4j.testing.evaluation.review.SampleChange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class EvalCommandTest {

    private static final String REPORT = """
            {"trials":1,"strategies":["s"],"evaluations":[
              {"name":"A","passed":true,"advisory":false,"expectedOutput":"yes","actualOutput":"yep"},
              {"name":"A","passed":false,"advisory":true,"expectedOutput":"ignored","actualOutput":"nope"},
              {"name":"B","passed":false,"advisory":false,"expectedOutput":"no","actualOutput":"maybe"}]}
            """;
    private static final String REFUSAL = "Not accepting: no terminal to confirm with. "
            + "Run this yourself, or pass --yes if a person asked for it.";

    @TempDir
    Path root;

    private Path pending(String id, ResultStatus status) {
        Comparison comparison = new Comparison(status, 5.0, OptionalDouble.of(-10), OptionalDouble.of(1),
                OptionalDouble.of(-12), OptionalDouble.of(-8), List.of(new SampleChange("A", 50, 100)), List.of("B"),
                List.of(), Optional.empty(), OptionalDouble.empty());
        Path file = root.resolve("target/evaluation-baselines").resolve(id + ".json");
        PendingFile.write(file, id, "run", "src/test/resources/evaluation-baselines/" + id + ".json", REPORT,
                comparison);
        return file;
    }

    private Path baseline(String id) {
        return root.resolve("src/test/resources/evaluation-baselines").resolve(id + ".json");
    }

    @Test
    void reviewWithNothingPendingPrintsMessageAndExitsZero() {
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "review");

        assertThat(exit).isZero();
        assertThat(terminal.output()).contains("No pending evaluation results");
    }

    @Test
    void reviewJsonPrintsJsonWithoutPrompting() throws Exception {
        pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = new FakeTerminal(true);

        int exit = EvalCommand.run(root, terminal, "review", "--json");

        assertThat(exit).isZero();
        assertThat(terminal.prompts).isEmpty();
        JsonNode json = new ObjectMapper().readTree(terminal.output());
        assertThat(json.get("evaluations").get(0).get("id").asString()).isEqualTo("t.A/one");
    }

    @Test
    void reviewJsonWithNothingPendingPrintsEmptyJson() throws Exception {
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "review", "--json");

        assertThat(exit).isZero();
        assertThat(new ObjectMapper().readTree(terminal.output()))
                .isEqualTo(new ObjectMapper().readTree("{\"evaluations\":[]}"));
    }

    @Test
    void reviewReportsSkippedFilesOnStderr() throws Exception {
        pending("t.A/one", ResultStatus.CHANGED);
        Path bad = root.resolve("target/evaluation-baselines/t.A/bad.json");
        Files.writeString(bad, "{ not json");
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "review");

        assertThat(exit).isZero();
        assertThat(terminal.output()).contains("t.A/one  changed");
        assertThat(terminal.errors()).startsWith("Skipped " + bad.toAbsolutePath().normalize() + ": ");
    }

    @Test
    void exceptionInCommandExitsOneWithOneLineOnStderr() throws Exception {
        pending("t.A/one", ResultStatus.CHANGED);
        // a file where the baseline directory should be, so the baseline cannot be written
        Files.createDirectories(root.resolve("src/test/resources"));
        Files.writeString(root.resolve("src/test/resources/evaluation-baselines"), "");
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "accept", "--yes", "t.A/one");

        assertThat(exit).isEqualTo(1);
        assertThat(terminal.errors().lines()).singleElement().asString().startsWith("Cannot write the baseline ");
    }

    @Test
    void reviewNonInteractiveListsIdsAndStatuses() {
        pending("t.A/one", ResultStatus.CHANGED);
        pending("t.A/two", ResultStatus.FAILED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "review");

        assertThat(exit).isZero();
        assertThat(terminal.output()).contains("t.A/one  changed").contains("t.A/two  failed");
    }

    @Test
    void reviewInteractiveAcceptsWithA() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = new FakeTerminal(true, "a");

        int exit = EvalCommand.run(root, terminal, "review");

        assertThat(exit).isZero();
        assertThat(baseline("t.A/one")).exists();
        assertThat(file).doesNotExist();
        assertThat(terminal.output())
                .contains("t.A/one  changed  1 of 1")
                .contains("sample")
                .contains("50.00%")
                .contains("100.00%")
                .contains("new")
                .contains("mean difference -10.00 points (95% CI -12.00 to -8.00), tolerance 5.00")
                .contains("[a] accept  [r] reject  [d] show responses  [s] skip  [q] quit");
    }

    @Test
    void reviewInteractiveShowsBaselineScoreOfRemovedSample() throws Exception {
        Comparison comparison = new Comparison(ResultStatus.CHANGED, 5.0, OptionalDouble.of(0), OptionalDouble.of(0),
                OptionalDouble.of(0), OptionalDouble.of(0), List.of(new SampleChange("A", 100, 100)), List.of(),
                List.of("C"), Optional.empty(), OptionalDouble.empty());
        PendingFile.write(root.resolve("target/evaluation-baselines/t.A/one.json"), "t.A/one", "run",
                "src/test/resources/evaluation-baselines/t.A/one.json", REPORT, comparison);
        Files.createDirectories(baseline("t.A/one").getParent());
        Files.writeString(baseline("t.A/one"), """
                {"formatVersion":1,"trials":1,"strategies":["s"],"samples":[
                  {"name":"A","tags":[],"blocking":{"passed":1,"total":1},"advisory":{"passed":0,"total":0}},
                  {"name":"C","tags":[],"blocking":{"passed":1,"total":4},"advisory":{"passed":0,"total":0}}]}
                """);
        FakeTerminal terminal = new FakeTerminal(true, "s");

        EvalCommand.run(root, terminal, "review");

        assertThat(terminal.output()).containsPattern("\\n  C +25\\.00% +removed\\n");
    }

    @Test
    void reviewInteractiveShowsAdvisoryDifference() {
        Comparison comparison = new Comparison(ResultStatus.CHANGED, 5.0, OptionalDouble.of(0), OptionalDouble.of(0),
                OptionalDouble.of(0), OptionalDouble.of(0), List.of(new SampleChange("A", 100, 100)), List.of(),
                List.of(), Optional.empty(), OptionalDouble.of(-100));
        PendingFile.write(root.resolve("target/evaluation-baselines/t.A/one.json"), "t.A/one", "run",
                "src/test/resources/evaluation-baselines/t.A/one.json", REPORT, comparison);
        FakeTerminal terminal = new FakeTerminal(true, "s");

        EvalCommand.run(root, terminal, "review");

        assertThat(terminal.output()).contains("advisory difference -100.00 points, not checked against the tolerance");
    }

    @Test
    void reviewInteractiveListsReceivedSamplesOfIncompatibleResult() {
        Comparison comparison = new Comparison(ResultStatus.INCOMPATIBLE, 5.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), List.of(), List.of(),
                List.of(), Optional.of("the trials differ"), OptionalDouble.empty());
        PendingFile.write(root.resolve("target/evaluation-baselines/t.A/one.json"), "t.A/one", "run",
                "src/test/resources/evaluation-baselines/t.A/one.json", REPORT, comparison);
        FakeTerminal terminal = new FakeTerminal(true, "s");

        EvalCommand.run(root, terminal, "review");

        assertThat(terminal.output())
                .containsPattern("\\n  A +100\\.00% +0\\.00%\\n")
                .containsPattern("\\n  B +0\\.00%\\n")
                .contains("the trials differ")
                .doesNotContain("advisory difference");
    }

    private Path pendingIn(String module, String id) throws Exception {
        Path dir = root.resolve(module);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Comparison comparison = new Comparison(ResultStatus.CHANGED, 5.0, OptionalDouble.of(0), OptionalDouble.of(0),
                OptionalDouble.of(0), OptionalDouble.of(0), List.of(new SampleChange("A", 100, 100)), List.of("B"),
                List.of(), Optional.empty(), OptionalDouble.empty());
        Path file = dir.resolve("target/evaluation-baselines").resolve(id + ".json");
        PendingFile.write(file, id, "run", "src/test/resources/evaluation-baselines/" + id + ".json", REPORT,
                comparison);
        return file;
    }

    @Test
    void sameIdInTwoModulesIsNamedWithItsModule() throws Exception {
        pendingIn("a", "faq");
        pendingIn("b", "faq");
        FakeTerminal terminal = new FakeTerminal(false);

        EvalCommand.run(root, terminal, "review");

        assertThat(terminal.output()).contains("a:faq  changed").contains("b:faq  changed");
    }

    @Test
    void ambiguousIdIsRefusedAndModuleQualifiedIdSelectsOne() throws Exception {
        Path a = pendingIn("a", "faq");
        Path b = pendingIn("b", "faq");
        FakeTerminal ambiguous = new FakeTerminal(false);

        assertThat(EvalCommand.run(root, ambiguous, "accept", "--yes", "faq")).isEqualTo(1);
        assertThat(ambiguous.errors()).contains("faq is in several modules, name one of: a:faq, b:faq");
        assertThat(a).exists();
        assertThat(b).exists();

        FakeTerminal qualified = new FakeTerminal(false);
        assertThat(EvalCommand.run(root, qualified, "accept", "--yes", "a:faq")).isEqualTo(0);
        assertThat(a).doesNotExist();
        assertThat(b).exists();
    }

    @Test
    void subcommandsHaveHelp() {
        for (String command : List.of("review", "accept", "reject")) {
            FakeTerminal terminal = new FakeTerminal(false);

            assertThat(EvalCommand.run(root, terminal, command, "--help")).isEqualTo(0);
            assertThat(terminal.output()).contains("Usage: langchain4j-eval " + command);
        }
    }

    @Test
    void reviewInteractiveSummarisesTheDecision() {
        Comparison comparison = new Comparison(ResultStatus.FAILED, 5.0, OptionalDouble.of(-50), OptionalDouble.of(0),
                OptionalDouble.of(-50), OptionalDouble.of(-50),
                List.of(new SampleChange("A", OptionalDouble.of(100), OptionalDouble.of(50), OptionalDouble.of(100),
                        OptionalDouble.of(0))),
                List.of("B"), List.of(), Optional.empty(), OptionalDouble.of(-100));
        PendingFile.write(root.resolve("target/evaluation-baselines/t.A/one.json"), "t.A/one", "run",
                "src/test/resources/evaluation-baselines/t.A/one.json", REPORT, comparison);
        FakeTerminal terminal = new FakeTerminal(true, "s");

        EvalCommand.run(root, terminal, "review");

        assertThat(terminal.output())
                .contains("1 compared, 1 new, 0 removed")
                .containsPattern("\\n +blocking +advisory\\n")
                .containsPattern("\\n  sample +baseline +received +baseline +received\\n")
                .containsPattern("\\n  A +100\\.00% +50\\.00% +100\\.00% +0\\.00% +regressed\\n")
                .containsPattern("\\n  B +new +0\\.00%\\n")
                .doesNotContain("warning");
    }

    @Test
    void reviewInteractiveWarnsWhenNothingWasCompared() {
        Comparison comparison = new Comparison(ResultStatus.CHANGED, 5.0, OptionalDouble.of(0), OptionalDouble.of(0),
                OptionalDouble.of(0), OptionalDouble.of(0), List.of(), List.of("A", "B"), List.of("X"),
                Optional.empty(), OptionalDouble.empty());
        PendingFile.write(root.resolve("target/evaluation-baselines/t.A/one.json"), "t.A/one", "run",
                "src/test/resources/evaluation-baselines/t.A/one.json", REPORT, comparison);
        FakeTerminal terminal = new FakeTerminal(true, "s");

        EvalCommand.run(root, terminal, "review");

        assertThat(terminal.output()).contains("0 compared, 2 new, 1 removed").contains(
                "warning: no sample has blocking evaluations both here and in the baseline, so nothing was checked "
                        + "against the tolerance");
    }

    @Test
    void responsesShowEachTrialOnceWithTheVerdictOfEveryStrategy() {
        Comparison comparison = new Comparison(ResultStatus.NEW, 5.0, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), List.of(), List.of("A"), List.of(), Optional.empty(),
                OptionalDouble.empty());
        PendingFile.write(root.resolve("target/evaluation-baselines/t.A/one.json"), "t.A/one", "run",
                "src/test/resources/evaluation-baselines/t.A/one.json", """
                        {"trials":2,"strategies":["Exact","Judge (advisory)"],"evaluations":[
                          {"name":"A","passed":true,"advisory":false,"expectedOutput":"yes","actualOutput":"yep",
                           "trial":1,"strategy":"Exact"},
                          {"name":"A","passed":false,"advisory":true,"expectedOutput":"yes","actualOutput":"yep",
                           "trial":1,"strategy":"Judge (advisory)","explanation":"too casual"},
                          {"name":"A","passed":false,"advisory":false,"expectedOutput":"yes","actualOutput":null,
                           "trial":2,"error":"java.net.SocketTimeoutException: Read timed out"}]}
                        """, comparison);
        FakeTerminal terminal = new FakeTerminal(true, "d", "s");

        EvalCommand.run(root, terminal, "review");

        String nl = System.lineSeparator();
        assertThat(terminal.output()).contains("A" + nl + "  expected: yes" + nl
                + "  trial 1: yep" + nl
                + "    passed  Exact" + nl
                + "    failed  Judge (advisory): too casual" + nl
                + "  trial 2: (no output)" + nl
                + "    failed  error: java.net.SocketTimeoutException: Read timed out" + nl);
    }

    private void pendingWithErrors() {
        PendingFile.write(root.resolve("target/evaluation-baselines/t.A/one.json"), "t.A/one", "run",
                "src/test/resources/evaluation-baselines/t.A/one.json", """
                        {"trials":1,"strategies":["s"],"evaluations":[
                          {"name":"A","passed":false,"advisory":false,"actualOutput":null,"trial":1,
                           "error":"java.lang.RuntimeException: credit_balance_exhausted"},
                          {"name":"B","passed":false,"advisory":false,"actualOutput":null,"trial":1,
                           "error":"java.lang.RuntimeException: credit_balance_exhausted"}]}
                        """,
                new Comparison(ResultStatus.NEW, 5.0, OptionalDouble.empty(), OptionalDouble.empty(),
                        OptionalDouble.empty(), OptionalDouble.empty(), List.of(), List.of("A", "B"), List.of(),
                        Optional.empty(), OptionalDouble.empty()));
    }

    @Test
    void reviewInteractiveWarnsAboutErrorsRightBeforeTheDecision() {
        pendingWithErrors();
        FakeTerminal terminal = new FakeTerminal(true, "s");

        EvalCommand.run(root, terminal, "review");

        String nl = System.lineSeparator();
        assertThat(terminal.output()).contains("warning: 2 of 2 evaluations ended in an error, so their scores say "
                + "nothing about the answers:" + nl + "  - RuntimeException: credit_balance_exhausted (2)" + nl
                + "[a] accept");
    }

    @Test
    void reviewJsonWritesToTheOutputFileOnly() throws Exception {
        pendingWithErrors();
        FakeTerminal terminal = new FakeTerminal(false);

        int exit = EvalCommand.run(root, terminal, "review", "--json", "--output", "target/review.json");

        assertThat(exit).isZero();
        assertThat(terminal.output().strip()).isEqualTo("Wrote 1 pending result to target/review.json");
        JsonNode json = new ObjectMapper().readTree(root.resolve("target/review.json").toFile());
        assertThat(json.get("evaluations").get(0).get("errors").asInt()).isEqualTo(2);
    }

    @Test
    void outputWithoutJsonIsRefused() {
        FakeTerminal terminal = new FakeTerminal(false);

        assertThat(EvalCommand.run(root, terminal, "review", "--output", "review.json")).isEqualTo(1);
        assertThat(terminal.errors()).contains("--output needs --json");
    }

    @Test
    void reviewInteractiveRejectsWithR() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);

        EvalCommand.run(root, new FakeTerminal(true, "r"), "review");

        assertThat(file).doesNotExist();
        assertThat(baseline("t.A/one")).doesNotExist();
    }

    @Test
    void reviewInteractiveShowsResponsesWithD() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = new FakeTerminal(true, "d", "s");

        int exit = EvalCommand.run(root, terminal, "review");

        assertThat(exit).isZero();
        assertThat(terminal.prompts).hasSize(2);
        assertThat(terminal.output()).contains("expected").contains("yes").contains("yep").contains("nope")
                .contains("maybe");
        assertThat(file).exists();
    }

    @Test
    void reviewQuitStopsBeforeNextResult() {
        pending("t.A/one", ResultStatus.CHANGED);
        pending("t.A/two", ResultStatus.CHANGED);

        EvalCommand.run(root, new FakeTerminal(true, "q"), "review");

        assertThat(PendingResults.scan(root)).hasSize(2);
    }

    @Test
    void reviewSkipsUnchangedResults() {
        pending("t.A/one", ResultStatus.UNCHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "review");

        assertThat(exit).isZero();
        assertThat(terminal.output()).contains("No pending evaluation results").doesNotContain("t.A/one");
    }

    @Test
    void acceptWithoutTerminalRefusesAndExitsOne() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "accept", "t.A/one");

        assertThat(exit).isEqualTo(1);
        assertThat(terminal.errors()).contains(REFUSAL);
        assertThat(file).exists();
        assertThat(baseline("t.A/one")).doesNotExist();
    }

    @Test
    void acceptWithYesWithoutTerminalAccepts() {
        pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "accept", "--yes", "t.A/one");

        assertThat(exit).isZero();
        assertThat(baseline("t.A/one")).exists();
        assertThat(terminal.output()).contains("Accepted 1 evaluation.")
                .contains("src/test/resources/evaluation-baselines/t.A/one.json");
    }

    @Test
    void acceptInteractiveAsksAndHonoursNo() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = new FakeTerminal(true, "n");

        int exit = EvalCommand.run(root, terminal, "accept", "t.A/one");

        assertThat(exit).isZero();
        assertThat(terminal.prompts).containsExactly("Accept 1 evaluation (1 changed)? [y/N] ");
        assertThat(file).exists();
        assertThat(baseline("t.A/one")).doesNotExist();
    }

    @Test
    void acceptInteractiveListsWhatItWouldAcceptByStatus() throws Exception {
        pending("t.A/one", ResultStatus.NEW);
        pending("t.A/two", ResultStatus.FAILED);
        pending("t.A/three", ResultStatus.INCOMPATIBLE);
        pendingIn("a", "faq");
        pendingIn("b", "faq");
        FakeTerminal terminal = new FakeTerminal(true, "n");

        EvalCommand.run(root, terminal, "accept", "--all");

        String nl = System.lineSeparator();
        assertThat(terminal.output()).contains("  t.A/one  new" + nl + "  t.A/three  incompatible" + nl
                + "  t.A/two  failed" + nl).contains("  a:faq  changed" + nl + "  b:faq  changed" + nl);
        assertThat(terminal.prompts)
                .containsExactly("Accept 5 evaluations (1 new, 2 changed, 1 failed, 1 incompatible)? [y/N] ");
    }

    @Test
    void acceptInteractiveHonoursYes() {
        pending("t.A/one", ResultStatus.CHANGED);

        int exit = EvalCommand.run(root, new FakeTerminal(true, "y"), "accept", "t.A/one");

        assertThat(exit).isZero();
        assertThat(baseline("t.A/one")).exists();
    }

    @Test
    void acceptAllAcceptsEveryPendingResultExceptUnchanged() {
        pending("t.A/one", ResultStatus.CHANGED);
        pending("t.B/two", ResultStatus.NEW);
        pending("t.B/same", ResultStatus.UNCHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "accept", "--all", "--yes");

        assertThat(exit).isZero();
        assertThat(baseline("t.A/one")).exists();
        assertThat(baseline("t.B/two")).exists();
        assertThat(baseline("t.B/same")).doesNotExist();
        assertThat(terminal.output()).contains("Accepted 2 evaluations.");
    }

    @Test
    void acceptUnknownIdExitsOne() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "accept", "--yes", "t.A/one", "t.A/nope");

        assertThat(exit).isEqualTo(1);
        assertThat(terminal.errors()).contains("No pending result for t.A/nope");
        assertThat(file).exists();
    }

    @Test
    void acceptUnchangedIdIsUnknown() {
        pending("t.A/same", ResultStatus.UNCHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "accept", "--yes", "t.A/same");

        assertThat(exit).isEqualTo(1);
        assertThat(terminal.errors()).contains("No pending result for t.A/same");
    }

    @Test
    void rejectWithoutTerminalRefuses() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "reject", "t.A/one");

        assertThat(exit).isEqualTo(1);
        assertThat(terminal.errors()).contains(REFUSAL.replace("Not accepting", "Not rejecting"));
        assertThat(file).exists();
    }

    @Test
    void rejectWithYesDeletesPendingFile() {
        Path file = pending("t.A/one", ResultStatus.CHANGED);
        FakeTerminal terminal = FakeTerminal.none();

        int exit = EvalCommand.run(root, terminal, "reject", "--yes", "--all");

        assertThat(exit).isZero();
        assertThat(file).doesNotExist();
        assertThat(baseline("t.A/one")).doesNotExist();
        assertThat(terminal.output()).contains("Rejected 1 evaluation.");
    }

    @Test
    void acceptWithoutIdsOrAllIsInvalidInputExitOne() {
        pending("t.A/one", ResultStatus.CHANGED);

        int exit = EvalCommand.run(root, FakeTerminal.none(), "accept", "--yes");

        assertThat(exit).isEqualTo(1);
        assertThat(PendingResults.scan(root)).hasSize(1);
    }
}
