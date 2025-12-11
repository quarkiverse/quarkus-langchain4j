package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("removal")
class YamlLoaderTest {

    @Test
    void loadShouldThrowExceptionWhenPathIsNull() {
        assertThatThrownBy(() -> YamlLoader.load(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source must not be null or blank");
    }

    @Test
    void loadShouldThrowExceptionWhenPathIsBlank() {
        assertThatThrownBy(() -> YamlLoader.load(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source must not be null or blank");
    }

    @Test
    void loadShouldThrowExceptionWhenFileDoesNotExist() {
        assertThatThrownBy(() -> YamlLoader.load("non-existent-file.yaml"))
                .isInstanceOf(SampleLoadException.class)
                .hasMessageStartingWith("File not found:");
    }

    @Test
    void loadShouldThrowExceptionWhenYamlIsEmpty(@TempDir Path tempDir) throws Exception {
        File emptyYaml = tempDir.resolve("empty.yaml").toFile();
        try (FileWriter writer = new FileWriter(emptyYaml)) {
            writer.write(""); // Write an empty YAML file
        }

        assertThatThrownBy(() -> YamlLoader.load(emptyYaml.getPath()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("YAML file is empty or invalid");
    }

    @Test
    void loadShouldThrowExceptionWhenExpectedOutputIsMissing(@TempDir Path tempDir) throws Exception {
        File invalidYaml = tempDir.resolve("invalid.yaml").toFile();
        try (FileWriter writer = new FileWriter(invalidYaml)) {
            writer.write(
                    """
                            - name: Sample1
                              parameters:
                                - param1
                            """); // Missing "expected-output" or "expected-outputs"
        }

        assertThatThrownBy(() -> YamlLoader.load(invalidYaml.getPath()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Expected output not found for sample 'Sample1'");
    }

    @SuppressWarnings("unchecked")
    @Test
    void loadShouldLoadValidYamlFile(@TempDir Path tempDir) throws Exception {
        File validYaml = tempDir.resolve("valid.yaml").toFile();
        try (FileWriter writer = new FileWriter(validYaml)) {
            writer.write(
                    """
                            - name: Sample1
                              parameters:
                                - p1
                                - p2
                              expected-output: ExpectedOutput1
                              tags:
                                - tag1
                                - tag2
                            - name: Sample2
                              parameters:
                                - p3
                              expected-outputs:
                                - Output1
                                - Output2
                            """);
        }

        Samples<Object> samples = YamlLoader.load(validYaml.getPath());

        assertThat(samples).hasSize(2);

        // Validate Sample1
        EvaluationSample<Object> sample1 = samples.get(0);
        assertThat(sample1.name()).isEqualTo("Sample1");
        assertThat(sample1.parameters()).containsExactly("p1", "p2");
        assertThat(sample1.expectedOutput()).isEqualTo("ExpectedOutput1");
        assertThat(sample1.tags()).containsExactly("tag1", "tag2");

        // Validate Sample2
        EvaluationSample<Object> sample2 = samples.get(1);
        assertThat(sample2.name()).isEqualTo("Sample2");
        assertThat(sample2.parameters()).containsExactly("p3");
        assertThat(sample2.expectedOutput()).isInstanceOf(List.class);
        assertThat((List<String>) sample2.expectedOutput()).containsExactly("Output1", "Output2");
        assertThat(sample2.tags()).isEmpty();
    }

    @Test
    void loadShouldHandleYamlWithMissingTags(@TempDir Path tempDir) throws Exception {
        File yamlWithoutTags = tempDir.resolve("no-tags.yaml").toFile();
        try (FileWriter writer = new FileWriter(yamlWithoutTags)) {
            writer.write(
                    """
                            - name: Sample1
                              parameters:
                                - p1
                              expected-output: ExpectedOutput1
                            """); // No "tags" field
        }

        Samples<Object> samples = YamlLoader.load(yamlWithoutTags.getPath());

        assertThat(samples).hasSize(1);

        EvaluationSample<Object> sample = samples.get(0);
        assertThat(sample.name()).isEqualTo("Sample1");
        assertThat(sample.parameters()).containsExactly("p1");
        assertThat(sample.expectedOutput()).isEqualTo("ExpectedOutput1");
        assertThat(sample.tags()).isEmpty(); // Ensure tags default to an empty list
    }

    @Test
    void loadShouldThrowExceptionOnInvalidYamlFormat(@TempDir Path tempDir) throws Exception {
        File invalidYaml = tempDir.resolve("invalid-format.yaml").toFile();
        try (FileWriter writer = new FileWriter(invalidYaml)) {
            writer.write(
                    """
                            - invalid:
                                key: value
                            """); // Invalid structure for samples
        }

        assertThatThrownBy(() -> YamlLoader.load(invalidYaml.getPath()))
                .isInstanceOf(RuntimeException.class);
    }
}
