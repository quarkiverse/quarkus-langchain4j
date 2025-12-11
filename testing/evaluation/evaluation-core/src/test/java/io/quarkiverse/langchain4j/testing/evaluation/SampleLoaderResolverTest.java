package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Plain JUnit 5 test (no @QuarkusTest) to verify ServiceLoader discovery works.
 * <p>
 * This test runs WITHOUT a CDI container, so it validates that the
 * SampleLoaderResolver falls back to Java ServiceLoader correctly.
 * </p>
 */
class SampleLoaderResolverTest {

    @Test
    void shouldLoadYamlSamplesViaServiceLoader() {
        // This test runs without CDI, so ServiceLoader should be used
        Samples<Object> samples = SampleLoaderResolver.load(
                "src/test/resources/test-samples.yaml",
                Object.class);

        assertThat(samples).isNotNull();
        assertThat(samples).hasSizeGreaterThan(0);
        assertThat(samples.get(0).name()).isEqualTo("Sample1");
    }

    @Test
    void shouldThrowExceptionForUnsupportedSource() {
        assertThatThrownBy(() -> SampleLoaderResolver.load("test.unsupported", Object.class))
                .isInstanceOf(SampleLoadException.class)
                .hasMessageContaining("No SampleLoader found supporting source");
    }

    @Test
    void shouldThrowExceptionForNullSource() {
        assertThatThrownBy(() -> SampleLoaderResolver.load(null, Object.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source must not be null");
    }

    @Test
    void shouldThrowExceptionForBlankSource() {
        assertThatThrownBy(() -> SampleLoaderResolver.load("  ", Object.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source must not be null or blank");
    }

    @Test
    void shouldThrowExceptionForNullOutputType() {
        assertThatThrownBy(() -> SampleLoaderResolver.load("test.yaml", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Output type must not be null");
    }

    @Test
    void shouldThrowExceptionForNonExistentFile() {
        assertThatThrownBy(() -> SampleLoaderResolver.load("non-existent.yaml", Object.class))
                .isInstanceOf(SampleLoadException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void shouldLoadJsonSamplesViaServiceLoader() {
        // This test verifies that custom loaders (JsonSampleLoader) work via ServiceLoader
        Samples<Object> samples = SampleLoaderResolver.load(
                "src/test/resources/test-samples.json",
                Object.class);

        assertThat(samples).isNotNull();
        assertThat(samples).hasSize(2);
        assertThat(samples.get(0).name()).isEqualTo("JsonSample1");
        assertThat(samples.get(1).name()).isEqualTo("JsonSample2");
    }
}
