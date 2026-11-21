package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

/**
 * Tests that {@code isNotWorseThanBaseline()} derives its id from the running test method, also for
 * parameterized tests, where the same id is asserted once per invocation.
 */
class BaselineIdTest {

    static final Path PENDING = Path.of("target/evaluation-baselines")
            .resolve(ParameterizedProbe.class.getName()).resolve("probe.json");

    @Test
    void idIsDerivedForParameterizedTestMethodAndSecondInvocationFails() throws IOException {
        Files.deleteIfExists(PENDING);

        Events tests = EngineTestKit
                .engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(ParameterizedProbe.class))
                .execute()
                .testEvents();

        tests.assertStatistics(stats -> stats.started(2).failed(2));
        List<String> messages = tests.failed().stream()
                .map(event -> event.getRequiredPayload(org.junit.platform.engine.TestExecutionResult.class)
                        .getThrowable().orElseThrow().getMessage())
                .toList();
        assertThat(messages.get(0)).startsWith("No baseline yet for " + ParameterizedProbe.class.getName() + "/probe,");
        assertThat(messages.get(1)).contains("was already compared with its baseline in this run");
        assertThat(PENDING).exists();
    }

    @Test
    void eachLauncherExecutionInTheSameJvmIsANewRun() throws IOException {
        Path pending = Path.of("target/evaluation-baselines").resolve(RerunProbe.class.getName()).resolve("probe.json");
        Files.deleteIfExists(pending);
        String previous = System.getProperty("quarkus.langchain4j.evaluation.run");
        try {
            String noBaseline = "No baseline yet for " + RerunProbe.class.getName() + "/probe,";
            assertThat(failures(RerunProbe.class)).singleElement(as(InstanceOfAssertFactories.STRING))
                    .startsWith(noBaseline);
            assertThat(failures(RerunProbe.class)).singleElement(as(InstanceOfAssertFactories.STRING))
                    .startsWith(noBaseline);
        } finally {
            if (previous == null) {
                System.clearProperty("quarkus.langchain4j.evaluation.run");
            } else {
                System.setProperty("quarkus.langchain4j.evaluation.run", previous);
            }
        }
    }

    /**
     * Run a test class through a new launcher, as Quarkus continuous testing or a Surefire rerun does.
     */
    private static List<String> failures(Class<?> testClass) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(testClass)).build(), listener);
        return listener.getSummary().getFailures().stream().map(f -> f.getException().getMessage()).toList();
    }

    static class RerunProbe {

        @Test
        void probe() {
            EvaluationSample<String> sample = new EvaluationSample<>("A", new Parameters(), "expected", List.of());
            EvaluationAssertions.assertThat(new EvaluationReport<>(List.of(
                    Scorer.EvaluationResult.fromCompletedEvaluation(sample, "x", EvaluationResult.fromBoolean(true)))))
                    .isNotWorseThanBaseline();
        }
    }

    static class ParameterizedProbe {

        @ParameterizedTest
        @ValueSource(ints = { 1, 2 })
        void probe(int ignored) {
            EvaluationSample<String> sample = new EvaluationSample<>("A", new Parameters(), "expected", List.of());
            EvaluationAssertions.assertThat(new EvaluationReport<>(List.of(
                    Scorer.EvaluationResult.fromCompletedEvaluation(sample, "x", EvaluationResult.fromBoolean(true)))))
                    .isNotWorseThanBaseline();
        }
    }
}
