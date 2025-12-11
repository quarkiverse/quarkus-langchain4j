package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test verifying that custom CDI-based sample loaders work correctly
 * when using the JUnit 5 extension with Quarkus.
 */
@QuarkusTest
@ExtendWith(EvaluationExtension.class)
class QuarkusCustomCdiLoaderIT {

    @Test
    void shouldLoadSamplesFromCustomCdiLoader(
            @SampleLocation("custom:greetings") Samples<String> samples) {
        assertThat(samples).isNotNull();
        assertThat(samples).hasSize(3);

        assertThat(samples.get(0).name()).isEqualTo("Greeting1");
        assertThat(samples.get(0).parameters()).hasSize(1);
        assertThat(samples.get(0).parameters().<String> get(0)).isEqualTo("Hello");
        assertThat(samples.get(0).expectedOutput()).isEqualTo("Hello, how can I help you?");
        assertThat(samples.get(0).tags()).contains("greeting", "english");

        assertThat(samples.get(1).name()).isEqualTo("Greeting2");
        assertThat(samples.get(1).parameters().<String> get(0)).isEqualTo("Bonjour");
        assertThat(samples.get(1).tags()).contains("greeting", "french");

        assertThat(samples.get(2).name()).isEqualTo("Greeting3");
        assertThat(samples.get(2).parameters().<String> get(0)).isEqualTo("Hola");
        assertThat(samples.get(2).tags()).contains("greeting", "spanish");
    }

    @Test
    void shouldLoadMathSamplesFromCustomCdiLoader(
            @SampleLocation("custom:math") Samples<String> samples) {
        assertThat(samples).isNotNull();
        assertThat(samples).hasSize(2);

        assertThat(samples.get(0).name()).isEqualTo("Math1");
        assertThat(samples.get(0).parameters().<String> get(0)).isEqualTo("What is 2 + 2?");
        assertThat(samples.get(0).expectedOutput()).isEqualTo("4");
        assertThat(samples.get(0).tags()).contains("math", "addition");

        assertThat(samples.get(1).name()).isEqualTo("Math2");
        assertThat(samples.get(1).parameters().<String> get(0)).isEqualTo("What is 10 * 5?");
        assertThat(samples.get(1).expectedOutput()).isEqualTo("50");
        assertThat(samples.get(1).tags()).contains("math", "multiplication");
    }

    @Test
    void shouldLoadProgrammingSamplesFromCustomCdiLoader(
            @SampleLocation("custom:programming") Samples<String> samples) {
        assertThat(samples).isNotNull();
        assertThat(samples).hasSize(2);

        assertThat(samples.get(0).name()).isEqualTo("Programming1");
        assertThat(samples.get(0).parameters().<String> get(0)).isEqualTo("What is a CDI bean?");
        assertThat(samples.get(0).expectedOutput())
                .isEqualTo("A CDI bean is a managed component in Jakarta EE that supports dependency injection.");
        assertThat(samples.get(0).tags()).contains("programming", "cdi");

        assertThat(samples.get(1).name()).isEqualTo("Programming2");
        assertThat(samples.get(1).parameters().<String> get(0)).isEqualTo("What is dependency injection?");
        assertThat(samples.get(1).tags()).contains("programming", "design-pattern");
    }

    @Test
    void shouldLoadMultipleSampleSourcesInSameTest(
            @SampleLocation("custom:greetings") Samples<String> greetings,
            @SampleLocation("custom:math") Samples<String> mathSamples,
            @SampleLocation("src/test/resources/quarkus-test-samples.yaml") Samples<String> yamlSamples) {
        assertThat(greetings).isNotNull().hasSize(3);
        assertThat(mathSamples).isNotNull().hasSize(2);
        assertThat(yamlSamples).isNotNull().hasSizeGreaterThanOrEqualTo(3);

        assertThat(greetings.get(0).name()).isEqualTo("Greeting1");
        assertThat(mathSamples.get(0).name()).isEqualTo("Math1");
        assertThat(yamlSamples.get(0).name()).isEqualTo("QuarkusSample1");
    }

    @Test
    void shouldVerifyCdiLoaderPriority(
            @SampleLocation("custom:greetings") Samples<String> samples) {
        assertThat(samples).isNotNull();
        assertThat(samples.get(0).name()).startsWith("Greeting");
    }
}
