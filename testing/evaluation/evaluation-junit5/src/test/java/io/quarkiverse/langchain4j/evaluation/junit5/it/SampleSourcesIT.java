package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleSources;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for @SampleSources annotation.
 * <p>
 * Verifies that samples from multiple sources can be combined into a single Samples instance.
 * </p>
 */
@QuarkusTest
@ExtendWith(EvaluationExtension.class)
class SampleSourcesIT {

    @Test
    void shouldCombineSamplesFromMultipleSources(
            @SampleSources({
                    @SampleLocation("src/test/resources/smoke-tests.yaml"),
                    @SampleLocation("src/test/resources/regression-tests.yaml")
            }) Samples<String> samples,
            Scorer scorer) {

        // Verify samples were combined
        assertThat(samples)
                .as("Combined samples should contain samples from both sources")
                .hasSize(4); // 2 from smoke + 2 from regression

        // Verify we can evaluate all samples
        var report = scorer.evaluate(samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        assertThat(report)
                .hasEvaluationCount(4)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(100.0);
    }

    @Test
    void shouldCombineThreeSources(
            @SampleSources({
                    @SampleLocation("src/test/resources/smoke-tests.yaml"),
                    @SampleLocation("src/test/resources/regression-tests.yaml"),
                    @SampleLocation("src/test/resources/edge-cases.yaml")
            }) Samples<String> samples,
            Scorer scorer) {

        assertThat(samples)
                .as("Combined samples should contain samples from all three sources")
                .hasSize(5); // 2 + 2 + 1

        var sampleNames = samples.stream()
                .map(sample -> sample.name())
                .toList();

        assertThat(sampleNames)
                .contains("Smoke Test 1", "Smoke Test 2")
                .contains("Regression Test 1", "Regression Test 2")
                .contains("Edge Case 1");
    }

    @Test
    void shouldFilterCombinedSamplesByTags(
            @SampleSources({
                    @SampleLocation("src/test/resources/smoke-tests.yaml"),
                    @SampleLocation("src/test/resources/regression-tests.yaml")
            }) Samples<String> samples,
            Scorer scorer) {

        // Filter combined samples by tag
        var smokeSamples = samples.filterByTags("smoke");

        assertThat(smokeSamples)
                .as("Should only contain smoke-tagged samples")
                .hasSize(2);

        var report = scorer.evaluate(smokeSamples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        assertThat(report)
                .hasEvaluationCount(2)
                .hasAllPassed()
                .hasPassedForTag("smoke");
    }

    @Test
    void shouldPreserveOrderFromSources(
            @SampleSources({
                    @SampleLocation("src/test/resources/smoke-tests.yaml"),
                    @SampleLocation("src/test/resources/regression-tests.yaml")
            }) Samples<String> samples) {

        var sampleNames = samples.stream()
                .map(sample -> sample.name())
                .toList();

        assertThat(sampleNames)
                .containsExactly(
                        "Smoke Test 1",
                        "Smoke Test 2",
                        "Regression Test 1",
                        "Regression Test 2");
    }
}
