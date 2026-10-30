package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvaluationStrategyTest {

    private final EvaluationSample<String> sample = new EvaluationSample<>("S", new Parameters(), "expected", List.of());

    @Test
    void strategiesAreBlockingByDefault() {
        EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult.fromBoolean(true);
        assertThat(strategy.isAdvisory()).isFalse();
    }

    @Test
    void advisoryWrapperDelegatesAndIsAdvisory() {
        EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult.failed(0.4, "nope");
        EvaluationStrategy<String> advisory = EvaluationStrategy.toAdvisory(strategy);

        assertThat(advisory.isAdvisory()).isTrue();
        assertThat(advisory.evaluate(sample, "x")).isEqualTo(EvaluationResult.failed(0.4, "nope"));
        assertThat(EvaluationStrategy.toAdvisory(advisory)).isSameAs(advisory);
    }

    @Test
    void advisoryWrapperRejectsNull() {
        assertThatThrownBy(() -> EvaluationStrategy.toAdvisory(null)).isInstanceOf(NullPointerException.class);
    }
}
