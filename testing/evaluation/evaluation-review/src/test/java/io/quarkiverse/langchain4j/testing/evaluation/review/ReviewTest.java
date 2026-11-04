package io.quarkiverse.langchain4j.testing.evaluation.review;

import static io.quarkiverse.langchain4j.testing.evaluation.review.ReviewTestSupport.REPORT;
import static io.quarkiverse.langchain4j.testing.evaluation.review.ReviewTestSupport.comparison;
import static io.quarkiverse.langchain4j.testing.evaluation.review.ReviewTestSupport.pending;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ReviewTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path root;

    private static Comparison changed() {
        return comparison(ResultStatus.CHANGED, List.of(new SampleChange("A", 50, 100)), List.of(), List.of());
    }

    @Test
    void acceptWritesSummaryIntoOwnModuleAndDeletesPendingFile() {
        Path fileA = pending(root.resolve("a"), "target", "t.A/one", REPORT, changed());
        Path fileB = pending(root.resolve("b"), "target", "t.B/two", REPORT, changed());

        PendingResults.scan(root).forEach(Review::accept);

        Path baselineA = root.resolve("a/src/test/resources/evaluation-baselines/t.A/one.json");
        Path baselineB = root.resolve("b/src/test/resources/evaluation-baselines/t.B/two.json");
        assertThat(BaselineSummary.read(baselineA).samples()).hasSize(2);
        assertThat(baselineB).exists();
        assertThat(root.resolve("a/src/test/resources/evaluation-baselines/t.B")).doesNotExist();
        assertThat(fileA).doesNotExist();
        assertThat(fileB).doesNotExist();
    }

    @Test
    void acceptWorksForEvaluationThatNoLongerExists() {
        // only the pending file exists: no test class, no baseline yet
        pending(root, "target", "gone.Test/removed", REPORT, changed());

        Review.accept(PendingResults.scan(root).get(0));

        assertThat(root.resolve("src/test/resources/evaluation-baselines/gone.Test/removed.json")).exists();
        assertThat(PendingResults.scan(root)).isEmpty();
    }

    @Test
    void rejectDeletesPendingFileOnly() {
        Path file = pending(root, "target", "t.A/one", REPORT, changed());

        Review.reject(PendingResults.scan(root).get(0));

        assertThat(file).doesNotExist();
        assertThat(root.resolve("src")).doesNotExist();
    }

    @Test
    void toJsonListsSamplesWithBaselineReceivedExpectedAndResponses() throws Exception {
        pending(root.resolve("mod"), "target", "t.A/one", REPORT, changed());

        JsonNode json = MAPPER.readTree(Review.toJson(root, PendingResults.scan(root)));

        JsonNode evaluation = json.get("evaluations").get(0);
        assertThat(evaluation.get("id").asString()).isEqualTo("t.A/one");
        assertThat(evaluation.get("module").asString()).isEqualTo("mod");
        assertThat(evaluation.get("status").asString()).isEqualTo("changed");
        assertThat(evaluation.get("baselineFile").asString())
                .isEqualTo("mod/src/test/resources/evaluation-baselines/t.A/one.json");
        assertThat(evaluation.get("comparison").get("tolerance").asDouble()).isEqualTo(5.0);
        JsonNode a = evaluation.get("samples").get(0);
        assertThat(a.get("name").asString()).isEqualTo("A");
        assertThat(a.get("baseline").asDouble()).isEqualTo(50);
        assertThat(a.get("received").asDouble()).isEqualTo(100);
        assertThat(a.get("expected").asString()).isEqualTo("yes");
        assertThat(a.get("responses")).hasSize(2);
        assertThat(a.get("responses").get(1).get("output").asString()).isEqualTo("nope");
        assertThat(a.get("responses").get(1).get("passed").asBoolean()).isFalse();
        assertThat(a.get("responses").get(1).get("advisory").asBoolean()).isTrue();
    }

    @Test
    void toJsonOmitsBaselineForAddedSamplesAndReceivedForRemovedOnes() throws Exception {
        Comparison comparison = comparison(ResultStatus.CHANGED, List.of(new SampleChange("A", 50, 100)),
                List.of("B"), List.of("C"));
        pending(root, "target", "t.A/one", REPORT, comparison);
        BaselineSummary.fromReport(MAPPER.readTree("""
                {"trials":1,"strategies":["s"],"evaluations":[
                  {"name":"C","passed":true,"advisory":false}]}""")).write(
                root.resolve("src/test/resources/evaluation-baselines/t.A/one.json"));

        JsonNode samples = MAPPER.readTree(Review.toJson(root, PendingResults.scan(root)))
                .get("evaluations").get(0).get("samples");

        assertThat(samples).hasSize(3);
        JsonNode added = samples.get(1);
        assertThat(added.get("name").asString()).isEqualTo("B");
        assertThat(added.has("baseline")).isFalse();
        assertThat(added.get("received").asDouble()).isEqualTo(0);
        JsonNode removed = samples.get(2);
        assertThat(removed.get("name").asString()).isEqualTo("C");
        assertThat(removed.get("baseline").asDouble()).isEqualTo(100);
        assertThat(removed.has("received")).isFalse();
        assertThat(Files.exists(root.resolve("target"))).isTrue();
    }

    @Test
    void toJsonListsReceivedSamplesOfAnIncompatibleResult() throws Exception {
        Comparison comparison = new Comparison(ResultStatus.INCOMPATIBLE, 5.0, java.util.OptionalDouble.empty(),
                java.util.OptionalDouble.empty(), java.util.OptionalDouble.empty(), java.util.OptionalDouble.empty(),
                List.of(), List.of(), List.of(), java.util.Optional.of("the trials differ"),
                java.util.OptionalDouble.empty());
        pending(root, "target", "t.A/one", REPORT, comparison);

        JsonNode samples = MAPPER.readTree(Review.toJson(root, PendingResults.scan(root)))
                .get("evaluations").get(0).get("samples");

        assertThat(samples).extracting(s -> s.get("name").asString()).containsExactly("A", "B");
        JsonNode a = samples.get(0);
        assertThat(a.has("baseline")).isFalse();
        assertThat(a.get("received").asDouble()).isEqualTo(100);
        assertThat(a.get("responses")).extracting(r -> r.get("output").asString()).containsExactly("yep", "nope");
    }

    @Test
    void toJsonReadsRemovedSamplesFromAFullReportBaseline() throws Exception {
        Comparison comparison = comparison(ResultStatus.CHANGED, List.of(new SampleChange("A", 100, 100)), List.of(),
                List.of("C"));
        pending(root, "target", "t.A/one", REPORT, comparison);
        Path baseline = root.resolve("src/test/resources/evaluation-baselines/t.A/one.json");
        Files.createDirectories(baseline.getParent());
        Files.writeString(baseline, """
                {"trials":1,"strategies":["s"],"evaluations":[
                  {"name":"A","passed":true,"advisory":false},
                  {"name":"C","passed":true,"advisory":false},
                  {"name":"C","passed":false,"advisory":false}]}""");

        JsonNode samples = MAPPER.readTree(Review.toJson(root, PendingResults.scan(root)))
                .get("evaluations").get(0).get("samples");

        assertThat(samples.get(1).get("name").asString()).isEqualTo("C");
        assertThat(samples.get(1).get("baseline").asDouble()).isEqualTo(50);
    }

    @Test
    void toJsonKeepsAdvisoryScoresApartFromBlockingOnes() throws Exception {
        Comparison comparison = comparison(ResultStatus.NEW, List.of(), List.of("J"), List.of());
        pending(root, "target", "t.A/one", """
                {"trials":1,"strategies":["judge (advisory)"],"evaluations":[
                  {"name":"J","passed":true,"advisory":true,"expectedOutput":"e","actualOutput":"a"},
                  {"name":"J","passed":false,"advisory":true,"expectedOutput":"e","actualOutput":"b"}]}""", comparison);

        JsonNode sample = MAPPER.readTree(Review.toJson(root, PendingResults.scan(root)))
                .get("evaluations").get(0).get("samples").get(0);

        assertThat(sample.get("name").asString()).isEqualTo("J");
        assertThat(sample.has("received")).isFalse();
        assertThat(sample.get("advisoryReceived").asDouble()).isEqualTo(50);
        assertThat(sample.get("responses")).hasSize(2);
    }

    @Test
    void toJsonDescribesEachResponseWithItsTrialStrategyAndReason() throws Exception {
        Comparison comparison = comparison(ResultStatus.NEW, List.of(), List.of("A"), List.of());
        pending(root, "target", "t.A/one", """
                {"trials":1,"strategies":["Judge"],"evaluations":[
                  {"name":"A","passed":false,"advisory":false,"expectedOutput":"e","actualOutput":"a",
                   "trial":1,"strategy":"Judge","explanation":"off topic"},
                  {"name":"A","passed":false,"advisory":false,"expectedOutput":"e","actualOutput":null,
                   "trial":2,"error":"java.lang.IllegalStateException: down"}]}""", comparison);

        JsonNode responses = MAPPER.readTree(Review.toJson(root, PendingResults.scan(root)))
                .get("evaluations").get(0).get("samples").get(0).get("responses");

        assertThat(responses.get(0).get("trial").asInt()).isEqualTo(1);
        assertThat(responses.get(0).get("strategy").asString()).isEqualTo("Judge");
        assertThat(responses.get(0).get("explanation").asString()).isEqualTo("off topic");
        assertThat(responses.get(1).get("output").isNull()).isTrue();
        assertThat(responses.get(1).has("strategy")).isFalse();
        assertThat(responses.get(1).get("error").asString()).isEqualTo("java.lang.IllegalStateException: down");
    }

    @Test
    void errorSummaryCountsEachDistinctErrorMostFrequentFirst() {
        assertThat(Review.errorSummary(List.of(), 4)).isEmpty();
        assertThat(Review.errorSummary(List.of("b", "a", "a", "c", "d", "e"), 8)).contains(
                "6 of 8 evaluations ended in an error, so their scores say nothing about the answers:\n"
                        + "  - a (2)\n  - b (1)\n  - c (1)\n  - and 2 other errors");
    }

    @Test
    void toJsonCountsTheEvaluationsThatEndedInAnError() throws Exception {
        pending(root, "target", "t.A/one", """
                {"trials":1,"strategies":["s"],"evaluations":[
                  {"name":"A","passed":false,"advisory":false,"actualOutput":null,"trial":1,"error":"quota"},
                  {"name":"B","passed":true,"advisory":false,"actualOutput":"ok","trial":1,"strategy":"s"}]}""",
                comparison(ResultStatus.NEW, List.of(), List.of("A", "B"), List.of()));

        JsonNode evaluation = MAPPER.readTree(Review.toJson(root, PendingResults.scan(root))).get("evaluations").get(0);

        assertThat(evaluation.get("errors").asInt()).isEqualTo(1);
    }

    @Test
    void errorSummaryGroupsByFirstLineCutShort() {
        String id1 = "dev.langchain4j.exception.RateLimitException: " + "q".repeat(120) + " request req_1";
        String id2 = "dev.langchain4j.exception.RateLimitException: " + "q".repeat(120) + " request req_2";
        String multiline = "java.lang.IllegalStateException: down\n\tat somewhere";

        assertThat(Review.errorSummary(List.of(id1, id2, multiline), 3)).contains(
                "3 of 3 evaluations ended in an error, so their scores say nothing about the answers:\n"
                        + "  - " + ("RateLimitException: " + "q".repeat(120)).substring(0, 100) + "… (2)\n"
                        + "  - IllegalStateException: down (1)");
    }

    @Test
    void errorSummaryNamesTheProviderErrorOfAJsonBody() {
        String openAi = """
                io.quarkiverse.langchain4j.QuarkusHttpException: {
                  "error": {
                    "message": "You exceeded your current quota, please check your plan and billing details.",
                    "type": "insufficient_quota",
                    "param": null,
                    "code": "insufficient_quota"
                  }
                }""";
        String anthropic = "io.quarkiverse.langchain4j.QuarkusHttpException: {\"type\":\"error\",\"error\":{\"type\":"
                + "\"invalid_request_error\",\"message\":\"Your credit balance is too low.\"},\"request_id\":\"req_%d\"}";

        assertThat(Review.errorSummary(List.of(openAi, openAi, anthropic.formatted(1), anthropic.formatted(2)), 4))
                .contains("4 of 4 evaluations ended in an error, so their scores say nothing about the answers:\n"
                        + "  - QuarkusHttpException: insufficient_quota: You exceeded your current quota, please check "
                        + "your plan an… (2)\n"
                        + "  - QuarkusHttpException: Your credit balance is too low. (2)");
    }
}
