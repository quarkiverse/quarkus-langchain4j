package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleSources;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;

@ExtendWith(EvaluationExtension.class)
class SampleSourcesTest {

    @Test
    void shouldCombineTwoSources(
            @SampleSources({
                    @SampleLocation("src/test/resources/smoke-tests.yaml"),
                    @SampleLocation("src/test/resources/regression-tests.yaml")
            }) Samples<String> samples) {

        assertThat(samples)
                .as("Should combine samples from both sources")
                .hasSize(4);

        var sampleNames = samples.stream()
                .map(sample -> sample.name())
                .toList();

        assertThat(sampleNames)
                .contains("Smoke Test 1")
                .contains("Regression Test 1");
    }

    @Test
    void shouldWorkWithSingleSource(
            @SampleSources({
                    @SampleLocation("src/test/resources/smoke-tests.yaml")
            }) Samples<String> samples) {

        assertThat(samples)
                .as("Should work with a single source in @SampleSources")
                .hasSize(2);
    }

    @Test
    void shouldCombineWithExistingSingleLocationAnnotation(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> singleSource,
            @SampleSources({
                    @SampleLocation("src/test/resources/smoke-tests.yaml"),
                    @SampleLocation("src/test/resources/edge-cases.yaml")
            }) Samples<String> multipleSources) {

        assertThat(singleSource)
                .as("Single @SampleLocation should still work")
                .hasSize(2);

        assertThat(multipleSources)
                .as("@SampleSources should work alongside @SampleLocation")
                .hasSize(3);
    }
}
