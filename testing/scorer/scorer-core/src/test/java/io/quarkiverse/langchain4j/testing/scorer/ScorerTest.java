package io.quarkiverse.langchain4j.testing.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
        EvaluationStrategy<String> strategy = (sample, actual) -> actual.equals(sample.expectedOutput());

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

        var actual = scorer.evaluate(samples, this::sleep, (sample, actualOutput) -> true);

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

        EvaluationStrategy<String> strategy = (s, actual) -> false;

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

        EvaluationStrategy<String> strategy1 = (s, actual) -> actual.equals("expected");
        EvaluationStrategy<String> strategy2 = (s, actual) -> actual.length() > 3;

        Samples<String> samples = new Samples<>(sample);
        EvaluationReport<String> report = scorer.evaluate(samples, mockFunction, strategy1, strategy2);

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(100.0); // Both strategies should pass for the sample.
        assertThat(report.evaluations()).hasSize(2); // One result per strategy.
        report.evaluations().forEach(e -> assertThat(e.passed()).isTrue());
    }
}
