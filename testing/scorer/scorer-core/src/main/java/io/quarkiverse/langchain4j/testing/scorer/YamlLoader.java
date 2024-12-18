package io.quarkiverse.langchain4j.testing.scorer;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Utility to load samples from a YAML file.
 */
public class YamlLoader {

    private YamlLoader() {
        // Avoid direct instantiation
    }

    /**
     * Load samples from a YAML file.
     *
     * @param path the path to the YAML file, must not be {@code null}
     * @return the samples, never {@code null}
     * @param <T> the type of the expected output from the samples.
     */
    @SuppressWarnings("unchecked")
    public static <T> Samples<T> load(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: " + path);
        }

        Yaml yaml = new Yaml();
        Iterable<Object> list;
        List<EvaluationSample<T>> samples = new ArrayList<>();
        try (var reader = new FileReader(file)) {
            list = yaml.load(reader);
            if (list == null) {
                throw new RuntimeException("Failed to load sample from " + path);
            }
            for (Object o : list) {
                // Expect Map
                Map<String, Object> map = (Map<String, Object>) o;
                String name = (String) map.get("name");
                List<String> params = (List<String>) map.get("parameters");
                String expected = (String) map.get("expected-output");
                List<String> expectedList = null;
                if (expected == null) {
                    expectedList = (List<String>) map.get("expected-outputs");
                }
                List<String> tags = (List<String>) map.get("tags");
                if (tags == null) {
                    tags = List.of();
                }
                Parameters in = new Parameters();
                if (params == null) {
                    throw new RuntimeException("Parameters not found for sample " + name);
                }
                for (String p : params) {
                    in.add(new Parameter.UnnamedParameter(p));
                }
                if (expectedList == null && expected == null) {
                    throw new RuntimeException("Expected output not found for sample " + name);
                }
                if (expectedList != null) {
                    samples.add(new EvaluationSample<>(name, in, (T) expectedList, tags));
                } else {
                    samples.add(new EvaluationSample<>(name, in, (T) expected, tags));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new Samples<>(samples);
    }
}
