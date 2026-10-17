package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScorerTest {

    private Scorer scorer;

    @AfterEach
    void tearDown() {
        if (scorer != null) {
            scorer.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void advisoryStrategyShouldBeReportedButNotScored() {
        scorer = new Scorer(1);
        EvaluationSample<String> sample = new EvaluationSample<>("Sample", new Parameters(), "expected", List.of());
        EvaluationStrategy<String> blocking = (s, actual) -> EvaluationResult.fromBoolean(true);
        EvaluationStrategy<String> judge = (s, actual) -> EvaluationResult.failed("judge disagrees");

        EvaluationReport<String> report = scorer.evaluate(new Samples<>(sample), params -> "expected",
                blocking, EvaluationStrategy.toAdvisory(judge));

        assertThat(report.evaluations()).hasSize(2);
        assertThat(report.evaluations().get(0).advisory()).isFalse();
        assertThat(report.evaluations().get(1).advisory()).isTrue();
        assertThat(report.evaluations().get(1).passed()).isFalse();
        assertThat(report.evaluations().get(1).explanation()).isEqualTo("judge disagrees");
        assertThat(report.score()).isEqualTo(100.0);
        assertThat(report.advisoryScore()).isEqualTo(0.0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void evaluateShouldReturnCorrectReport() {
        scorer = new Scorer(2);

        EvaluationSample<String> sample1 = new EvaluationSample<>(
                "Sample1",
                new Parameters().add(new Parameter.UnnamedParameter("param1")),
                "expected1:param1",
                List.of("tag1", "tag2"));

        EvaluationSample<String> sample2 = new EvaluationSample<>(
                "Sample2",
                new Parameters().add(new Parameter.UnnamedParameter("param2")),
                "expected2",
                List.of("tag2"));

        Function<Parameters, String> mockFunction = params -> "expected1:param1";
        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult
                .fromBoolean(actual.equals(sample.expectedOutput()));

        Samples<String> samples = new Samples<>(sample1, sample2);
        EvaluationReport<String> report = scorer.evaluate(samples, mockFunction, strategy);

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(50.0); // Only one sample should pass.
        assertThat(report.evaluations()).hasSize(2);

        var actualEvaluations = report.evaluations().stream()
                .map(
                        e -> "%s[%s;%s=%s]"
                                .formatted(
                                        e.sample().name(), e.sample().expectedOutput(), e.result(), e.passed()))
                .toList();
        assertThat(actualEvaluations)
                .containsExactly(
                        "Sample1[expected1:param1;expected1:param1=true]",
                        "Sample2[expected2;expected1:param1=false]");
    }

    @SuppressWarnings("unchecked")
    @Test
    void evaluateShouldReturnCorrectlyOrderedReport() {
        scorer = new Scorer(2);
        var sleeps = Stream.of(25l, 0l);
        var samples = new Samples<>(
                sleeps
                        .map(
                                sleep -> new EvaluationSample<>(
                                        "%s".formatted(sleep),
                                        new Parameters().add(new Parameter.UnnamedParameter(sleep)),
                                        "irrelevant-for-this-test",
                                        List.of()))
                        .toList());

        var actual = scorer.evaluate(samples, this::sleep, (sample, actualOutput) -> EvaluationResult.fromBoolean(true));

        var actualOrder = actual.evaluations().stream().map(e -> e.sample().name()).toList();
        assertThat(actualOrder).containsExactly("25", "0");
    }

    private String sleep(Parameters params) {
        long ms = params.get(0);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "sleeped %s".formatted(ms);
    };

    @Test
    @SuppressWarnings("unchecked")
    void evaluateShouldHandleExceptionsInFunction() {
        scorer = new Scorer();
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add(new Parameter.UnnamedParameter("param1")),
                "expected",
                List.of());

        Function<Parameters, String> mockFunction = params -> {
            throw new RuntimeException("Test exception");
        };

        EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult.fromBoolean(false);

        Samples<String> samples = new Samples<>(sample);
        EvaluationReport<String> report = scorer.evaluate(samples, mockFunction, strategy);

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(0.0); // All evaluations should fail.
        assertThat(report.evaluations()).hasSize(1);
        assertThat(report.evaluations().get(0).passed()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluateShouldHandleMultipleStrategies() {
        scorer = new Scorer();

        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add(new Parameter.UnnamedParameter("param1")),
                "expected",
                List.of());

        Function<Parameters, String> mockFunction = params -> "expected";

        EvaluationStrategy<String> strategy1 = (s, actual) -> EvaluationResult.fromBoolean(actual.equals("expected"));
        EvaluationStrategy<String> strategy2 = (s, actual) -> EvaluationResult.fromBoolean(actual.length() > 3);

        Samples<String> samples = new Samples<>(sample);
        EvaluationReport<String> report = scorer.evaluate(samples, mockFunction, strategy1, strategy2);

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(100.0); // Both strategies should pass for the sample.
        assertThat(report.evaluations()).hasSize(2); // One result per strategy.
        report.evaluations().forEach(e -> assertThat(e.passed()).isTrue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void evaluateShouldRunEachSampleForTheConfiguredNumberOfTrials() {
        scorer = new Scorer(2, 3);

        EvaluationSample<String> flaky = new EvaluationSample<>(
                "Flaky", new Parameters().add(new Parameter.UnnamedParameter("flaky")), "ok", List.of("flaky"));
        EvaluationSample<String> stable = new EvaluationSample<>(
                "Stable", new Parameters().add(new Parameter.UnnamedParameter("stable")), "ok", List.of("stable"));

        // The flaky sample answers wrongly on every second call, the stable one always answers correctly.
        AtomicInteger flakyCalls = new AtomicInteger();
        Function<Parameters, String> function = params -> "flaky".equals(params.get(0))
                && flakyCalls.incrementAndGet() % 2 == 0 ? "ko" : "ok";
        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult
                .fromBoolean(actual.equals(sample.expectedOutput()));

        EvaluationReport<String> report = scorer.evaluate(new Samples<>(flaky, stable), function, strategy);

        assertThat(report.trials()).isEqualTo(3);
        assertThat(report.evaluations()).hasSize(6);
        assertThat(report.evaluations().stream().map(e -> e.sample().name()).toList())
                .containsExactly("Flaky", "Flaky", "Flaky", "Stable", "Stable", "Stable");
        assertThat(report.scoreForSample("Flaky")).isCloseTo(66.67, within(0.01));
        assertThat(report.scoreForSample("Stable")).isEqualTo(100.0);
        assertThat(report.score()).isCloseTo(83.33, within(0.01));
        assertThat(report.scoreForTag("flaky")).isCloseTo(66.67, within(0.01));
    }

    @Test
    void trialsMustBeAtLeastOne() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Scorer(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
