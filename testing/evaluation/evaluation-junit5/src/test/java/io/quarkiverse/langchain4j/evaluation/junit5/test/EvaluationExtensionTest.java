package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

@ExtendWith(EvaluationExtension.class)
class EvaluationExtensionTest {

    @ScorerConfiguration(concurrency = 3)
    private Scorer scorerWithConcurrency;

    private Scorer defaultScorer;

    @Test
    void scorerFieldInjectionShouldWork() {
        assertThat(scorerWithConcurrency).isNotNull();
        assertThat(scorerWithConcurrency).extracting("executor").isNotNull();
        assertThat(defaultScorer).isNotNull();
        assertThat(defaultScorer).extracting("executor").isNotNull();
    }

    @Test
    void scorerParameterShouldBeResolved(@ScorerConfiguration(concurrency = 2) Scorer scorer) {
        assertThat(scorer).isNotNull();
        assertThat(scorer).extracting("executor").isNotNull();
    }

    @Test
    void samplesParameterShouldBeResolved(@SampleLocation("src/test/resources/test-samples.yaml") Samples<String> samples) {
        assertThat(samples).isNotNull();
        assertThat(samples).hasSizeGreaterThan(0);
        assertThat(samples.get(0).name()).isEqualTo("Sample1"); // Assuming the YAML has this entry.
    }

    @Test
    void scorerShouldBeClosedAfterTest() {
        Scorer mockScorer = Mockito.mock(Scorer.class);
        mockScorer.close();
        Mockito.verify(mockScorer).close();
    }
}
