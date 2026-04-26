package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationDisplayNameGenerator;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test verifying that YAML sample loading works correctly
 * when using the JUnit 5 extension with Quarkus.
 */
@QuarkusTest
@ExtendWith(EvaluationExtension.class)
@DisplayNameGeneration(EvaluationDisplayNameGenerator.class)
class QuarkusYamlSampleLoadingIT {

    @Test
    void shouldLoadYamlSamplesViaQuarkusCDI(
            @SampleLocation("src/test/resources/quarkus-test-samples.yaml") Samples<String> samples) {
        assertThat(samples).isNotNull();
        assertThat(samples).hasSizeGreaterThanOrEqualTo(3);

        assertThat(samples.get(0).name()).isEqualTo("QuarkusSample1");
        assertThat(samples.get(0).parameters()).hasSize(1);
        assertThat(samples.get(0).parameters().<String> get(0)).isEqualTo("What is Quarkus?");
        assertThat(samples.get(0).expectedOutput()).isNotNull();
        assertThat(samples.get(0).tags()).contains("quarkus", "java");

        assertThat(samples.get(1).name()).isEqualTo("QuarkusSample2");
        assertThat(samples.get(1).parameters()).hasSize(1);
        assertThat(samples.get(1).tags()).contains("quarkus", "benefits");

        assertThat(samples.get(2).name()).isEqualTo("QuarkusSample3");
        assertThat(samples.get(2).parameters()).hasSize(1);
        assertThat(samples.get(2).tags()).contains("quarkus", "cdi");
    }

    @Test
    void shouldLoadMultipleYamlSamplesInSameTest(
            @SampleLocation("src/test/resources/quarkus-test-samples.yaml") Samples<String> samples1,
            @SampleLocation("src/test/resources/test-samples.yaml") Samples<String> samples2) {
        assertThat(samples1).isNotNull();
        assertThat(samples1).hasSizeGreaterThanOrEqualTo(3);

        assertThat(samples2).isNotNull();
        assertThat(samples2).hasSizeGreaterThanOrEqualTo(2);

        assertThat(samples1.get(0).name()).isEqualTo("QuarkusSample1");
        assertThat(samples2.get(0).name()).isEqualTo("Sample1");
    }
}
