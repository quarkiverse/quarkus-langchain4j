package io.quarkiverse.langchain4j.scorer.junit5.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.scorer.junit5.SampleLocation;
import io.quarkiverse.langchain4j.scorer.junit5.ScorerExtension;
import io.quarkiverse.langchain4j.testing.scorer.Samples;

@ExtendWith(ScorerExtension.class)
class ScorerExtensionTest {

    @Test
    void samplesParameterShouldBeResolved(@SampleLocation("src/test/resources/test-samples.yaml") Samples<String> samples) {
        assertThat(samples).isNotNull();
        assertThat(samples).hasSizeGreaterThan(0);
        assertThat(samples.get(0).name()).isEqualTo("Sample1"); // Assuming the YAML has this entry.
    }
}
