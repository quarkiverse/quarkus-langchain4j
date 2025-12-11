package io.quarkiverse.langchain4j.testing.evaluation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Example SampleLoader implementation for JSON files.
 * <p>
 * This loader demonstrates how to create custom loaders for different file formats.
 * It supports loading evaluation samples from JSON files with .json extension.
 * </p>
 * <p>
 * Expected JSON structure:
 * </p>
 *
 * <pre>{@code
 * [
 *   {
 *     "name": "Sample1",
 *     "parameters": ["param1", "param2"],
 *     "expected-output": "output",
 *     "tags": ["tag1"]
 *   }
 * ]
 * }</pre>
 *
 * <p>
 * This is a test implementation to demonstrate the SampleLoader SPI extensibility.
 * </p>
 */
@ApplicationScoped
public class JsonSampleLoader implements SampleLoader<Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String source) {
        return source != null && source.endsWith(".json");
    }

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

        try {
            List<Map<String, Object>> jsonData = objectMapper.readValue(file, List.class);
            List<EvaluationSample<Object>> samples = new ArrayList<>();

            for (Map<String, Object> sampleMap : jsonData) {
                EvaluationSample<Object> sample = parseSample(sampleMap, source);
                samples.add(sample);
            }

            return new Samples<>(samples);

        } catch (IOException e) {
            throw new SampleLoadException("Failed to parse JSON file: " + source, e);
        }
    }

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

    @Override
    public int priority() {
        return 0; // Same priority as YAML loader
    }
}
