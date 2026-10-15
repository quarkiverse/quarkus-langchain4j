package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationFunction;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationTest;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;

/**
 * Tests that {@code @EvaluationTest(trials = N)} runs every sample N times.
 */
class EvaluationTestTrialsTest {

    static final Path REPORT = Path.of("target/evaluation-reports/trials-test/echoWithTrials.json");

    @Test
    void shouldRunEachSampleForTheConfiguredNumberOfTrials() throws IOException {
        Files.deleteIfExists(REPORT);

        Events tests = EngineTestKit
                .engine("junit-jupiter")
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors
                        .selectClass(TrialsTest.class))
                .execute()
                .testEvents();

        tests.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        String json = Files.readString(REPORT);
        assertThat(json).contains("\"trials\": 3");
        int samples = intField(json, "totalSamples");
        assertThat(samples).isPositive();
        assertThat(intField(json, "totalEvaluations")).isEqualTo(3 * samples);
    }

    private static int intField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\": (\\d+)").matcher(json);
        assertThat(matcher.find()).as("field %s in %s", name, json).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    @ExtendWith(EvaluationExtension.class)
    static class TrialsTest {

        @EvaluationFunction("echo")
        Function<Parameters, String> echo() {
            return params -> params.<String> get(0);
        }

        @EvaluationTest(samples = "src/test/resources/smoke-tests.yaml", function = "echo", trials = 3, minScore = 100.0, reportFormats = {
                "json" }, reportOutputDir = "target/evaluation-reports/trials-test")
        void echoWithTrials() {
        }
    }
}
