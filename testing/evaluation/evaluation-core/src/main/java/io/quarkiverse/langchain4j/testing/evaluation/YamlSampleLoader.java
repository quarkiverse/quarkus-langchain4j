package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * SampleLoader implementation for YAML files.
 * <p>
 * This loader supports loading evaluation samples from YAML files with .yaml or .yml extensions.
 * </p>
 * <p>
 * Expected YAML structure:
 * </p>
 *
 * <pre>{@code
 * - name: "Sample1"
 *   parameters:
 *     - "param1"
 *     - "param2"
 *   expected-output: "output"
 *   tags:
 *     - "tag1"
 * }</pre>
 *
 * <p>
 * This implementation is registered as a Java service (META-INF/services) and is
 * discovered via ServiceLoader. The hybrid discovery in {@link SampleLoaderResolver}
 * ensures this loader is available in all contexts (plain JUnit tests, @QuarkusTest, etc.).
 * </p>
 */
public class YamlSampleLoader implements SampleLoader<Object> {

    /**
     * Check if this loader supports the given source.
     *
     * @param source source identifier (file path, URL, etc.)
     * @return true if source ends with .yaml or .yml
     */
    @Override
    public boolean supports(String source) {
        return source != null && (source.endsWith(".yaml") || source.endsWith(".yml"));
    }

    /**
     * Load samples from a YAML file.
     *
     * @param source path to the YAML file
     * @param outputType class representing the expected output type
     * @return loaded samples
     * @throws SampleLoadException if loading fails
     */
    @Override
    @SuppressWarnings("unchecked")
    public Samples<Object> load(String source, Class<Object> outputType) throws SampleLoadException {
        if (source == null || source.isBlank()) {
            throw new SampleLoadException("Source path must not be null or blank");
        }

        File file = new File(source);
        if (!file.exists()) {
            throw new SampleLoadException("File not found: " + source);
        }

        Yaml yaml = new Yaml();
        List<EvaluationSample<Object>> samples = new ArrayList<>();

        try (FileReader reader = new FileReader(file)) {
            Iterable<Object> yamlData = yaml.load(reader);

            if (yamlData == null) {
                throw new SampleLoadException("YAML file is empty or invalid: " + source);
            }

            for (Object item : yamlData) {
                if (!(item instanceof Map)) {
                    throw new SampleLoadException("Invalid YAML structure: expected map, got " + item.getClass());
                }

                Map<String, Object> sampleMap = (Map<String, Object>) item;
                EvaluationSample<Object> sample = parseSample(sampleMap, source);
                samples.add(sample);
            }

        } catch (SampleLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SampleLoadException("Failed to load YAML file: " + source, e);
        }

        return new Samples<>(samples);
    }

    /**
     * Parse a single sample from a YAML map.
     *
     * @param sampleMap the YAML map representing a sample
     * @param source the source file path (for error messages)
     * @return parsed evaluation sample
     * @throws SampleLoadException if parsing fails
     */
    @SuppressWarnings("unchecked")
    private EvaluationSample<Object> parseSample(Map<String, Object> sampleMap, String source)
            throws SampleLoadException {

        // Parse name (required)
        String name = (String) sampleMap.get("name");
        if (name == null || name.isBlank()) {
            throw new SampleLoadException("Sample name is required in: " + source);
        }

        // Parse parameters (required)
        List<String> paramList = (List<String>) sampleMap.get("parameters");
        if (paramList == null) {
            throw new SampleLoadException("Parameters not found for sample '" + name + "' in: " + source);
        }

        Parameters parameters = new Parameters();
        for (String param : paramList) {
            parameters.add(new Parameter.UnnamedParameter(param));
        }

        // Parse expected output (required - either single or list)
        String expectedOutput = (String) sampleMap.get("expected-output");
        List<String> expectedOutputList = null;

        if (expectedOutput == null) {
            expectedOutputList = (List<String>) sampleMap.get("expected-outputs");
        }

        if (expectedOutput == null && expectedOutputList == null) {
            throw new SampleLoadException(
                    "Expected output not found for sample '" + name + "' in: " + source);
        }

        // Parse tags (optional)
        List<String> tags = (List<String>) sampleMap.get("tags");
        if (tags == null) {
            tags = List.of();
        }

        // Create the evaluation sample
        Object output = expectedOutputList != null ? expectedOutputList : expectedOutput;
        return new EvaluationSample<>(name, parameters, output, tags);
    }

    /**
     * Get the priority of this loader.
     * <p>
     * YAML loader has default priority (0).
     * </p>
     *
     * @return 0
     */
    @Override
    public int priority() {
        return 0;
    }
}
