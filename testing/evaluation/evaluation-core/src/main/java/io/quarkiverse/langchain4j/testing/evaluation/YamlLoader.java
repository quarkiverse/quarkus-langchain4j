package io.quarkiverse.langchain4j.testing.evaluation;

/**
 * Utility to load samples from a YAML file.
 * <p>
 * This class is kept for backward compatibility but now delegates to
 * {@link SampleLoaderResolver} which uses the SampleLoader SPI.
 * </p>
 *
 * @deprecated Use {@link SampleLoaderResolver#load(String, Class)} instead.
 *             This class will be removed in a future version.
 */
@Deprecated(since = "3.0", forRemoval = true)
public class YamlLoader {

    private YamlLoader() {
        // Avoid direct instantiation
    }

    /**
     * Load samples from a YAML file.
     * <p>
     * This method now delegates to {@link SampleLoaderResolver#load(String, Class)}
     * which uses the hybrid CDI/ServiceLoader approach.
     * </p>
     *
     * @param path the path to the YAML file, must not be {@code null}
     * @return the samples, never {@code null}
     * @param <T> the type of the expected output from the samples.
     */
    @SuppressWarnings("unchecked")
    public static <T> Samples<T> load(String path) {
        return (Samples<T>) SampleLoaderResolver.load(path, Object.class);
    }
}
