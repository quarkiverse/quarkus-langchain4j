package io.quarkiverse.langchain4j.testing.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

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
                "expected1",
                List.of("tag1", "tag2"));

        EvaluationSample<String> sample2 = new EvaluationSample<>(
                "Sample2",
                new Parameters().add(new Parameter.UnnamedParameter("param2")),
                "expected2",
                List.of("tag2"));

        Function<Parameters, String> mockFunction = params -> "expected1";
        EvaluationStrategy<String> strategy = (sample, actual) -> actual.equals(sample.expectedOutput());

        Samples<String> samples = new Samples<>(sample1, sample2);
        EvaluationReport report = scorer.evaluate(samples, mockFunction, strategy);

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(50.0); // Only one sample should pass.
        assertThat(report.evaluations()).hasSize(2);

        Scorer.EvaluationResult<?> result1 = report.evaluations().get(0);
        assertThat(result1.passed()).isTrue();

        Scorer.EvaluationResult<?> result2 = report.evaluations().get(1);
        assertThat(result2.passed()).isFalse();
    }

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
        EvaluationReport report = scorer.evaluate(samples, mockFunction, strategy);

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
        EvaluationReport report = scorer.evaluate(samples, mockFunction, strategy1, strategy2);

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(100.0); // Both strategies should pass for the sample.
        assertThat(report.evaluations()).hasSize(2); // One result per strategy.
        report.evaluations().forEach(e -> assertThat(e.passed()).isTrue());
    }
}
