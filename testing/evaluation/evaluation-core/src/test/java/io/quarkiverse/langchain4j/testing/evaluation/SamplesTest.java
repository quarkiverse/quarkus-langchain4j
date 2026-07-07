package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class SamplesTest {

    @Test
    void constructorShouldCreateSamplesFromList() {
        EvaluationSample<String> sample1 = EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(Parameters.of("param1"))
                .withExpectedOutput("Output1")
                .build();

        EvaluationSample<String> sample2 = EvaluationSample.<String> builder()
                .withName("Sample2")
                .withParameters(Parameters.of("param2"))
                .withExpectedOutput("Output2")
                .build();

        Samples<String> samples = new Samples<>(List.of(sample1, sample2));

        assertThat(samples).hasSize(2);
        assertThat(samples.get(0)).isEqualTo(sample1);
        assertThat(samples.get(1)).isEqualTo(sample2);
    }

    @Test
    void constructorShouldCreateSamplesFromVarargs() {
        EvaluationSample<String> sample1 = EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(Parameters.of("param1"))
                .withExpectedOutput("Output1")
                .build();

        EvaluationSample<String> sample2 = EvaluationSample.<String> builder()
                .withName("Sample2")
                .withParameters(Parameters.of("param2"))
                .withExpectedOutput("Output2")
                .build();

        Samples<String> samples = new Samples<>(sample1, sample2);

        assertThat(samples).hasSize(2);
        assertThat(samples.get(0)).isEqualTo(sample1);
        assertThat(samples.get(1)).isEqualTo(sample2);
    }

    @Test
    void constructorShouldCreateEmptySamplesFromNullList() {
        Samples<String> samples = new Samples<>((List<EvaluationSample<String>>) null);

        assertThat(samples).isEmpty();
    }

    @Test
    void constructorShouldCreateEmptySamplesFromNullVarargs() {
        Samples<String> samples = new Samples<>((EvaluationSample<String>[]) null);

        assertThat(samples).isEmpty();
    }

    @Test
    void constructorShouldCreateEmptySamplesFromEmptyList() {
        Samples<String> samples = new Samples<>(List.of());

        assertThat(samples).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void constructorShouldCreateEmptySamplesFromEmptyVarargs() {
        Samples<String> samples = new Samples<>();

        assertThat(samples).isEmpty();
    }

    @Test
    void getShouldReturnSampleAtSpecifiedIndex() {
        EvaluationSample<String> sample1 = EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(Parameters.of("param1"))
                .withExpectedOutput("Output1")
                .build();

        EvaluationSample<String> sample2 = EvaluationSample.<String> builder()
                .withName("Sample2")
                .withParameters(Parameters.of("param2"))
                .withExpectedOutput("Output2")
                .build();

        Samples<String> samples = new Samples<>(sample1, sample2);

        assertThat(samples.get(0)).isEqualTo(sample1);
        assertThat(samples.get(1)).isEqualTo(sample2);
    }

    @Test
    void sizeShouldReturnNumberOfSamples() {
        EvaluationSample<String> sample1 = EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(Parameters.of("param1"))
                .withExpectedOutput("Output1")
                .build();

        EvaluationSample<String> sample2 = EvaluationSample.<String> builder()
                .withName("Sample2")
                .withParameters(Parameters.of("param2"))
                .withExpectedOutput("Output2")
                .build();

        Samples<String> samples = new Samples<>(sample1, sample2);

        assertThat(samples.size()).isEqualTo(2);
    }
}
