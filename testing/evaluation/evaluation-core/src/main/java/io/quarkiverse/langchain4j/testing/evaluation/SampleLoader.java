package io.quarkiverse.langchain4j.testing.evaluation;

/**
 * Service Provider Interface for loading evaluation samples.
 * <p>
 * Implementations can load samples from various sources: YAML, JSON, CSV,
 * databases, REST APIs, etc.
 * </p>
 * <p>
 * This interface supports both ServiceLoader (for plain JUnit tests) and
 * CDI (for Quarkus runtime and @QuarkusTest). Implementations should be
 * registered in META-INF/services and optionally annotated with CDI scopes.
 * </p>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;ApplicationScoped // Optional: enables CDI in Quarkus context
 *     public class YamlSampleLoader implements SampleLoader<Object> {
 *         &#64;Override
 *         public boolean supports(String source) {
 *             return source.endsWith(".yaml") || source.endsWith(".yml");
 *         }
 *
 *         @Override
 *         public Samples<Object> load(String source, Class<Object> outputType) {
 *             // Load samples from YAML file
 *         }
 *     }
 * }
 * </pre>
 *
 * @param <T> the type of expected output
 */
public interface SampleLoader<T> {

    /**
     * Check if this loader supports the given source URI.
     * <p>
     * This method is called to determine which loader should handle a given source.
     * Multiple loaders may support the same source; in that case, the loader with
     * the highest priority is selected.
     * </p>
     *
     * @param source source identifier (file path, URL, etc.)
     * @return true if this loader can handle the source
     */
    boolean supports(String source);

    /**
     * Load samples from the given source.
     * <p>
     * This method is called after {@link #supports(String)} returns true.
     * </p>
     *
     * @param source source identifier
     * @param outputType class representing the expected output type
     * @return loaded samples
     * @throws SampleLoadException if loading fails
     */
    Samples<T> load(String source, Class<T> outputType) throws SampleLoadException;

    /**
     * Get the priority of this loader (higher = higher priority).
     * <p>
     * Used when multiple loaders support the same source. The loader with
     * the highest priority value is selected.
     * </p>
     *
     * @return priority value (default: 0)
     */
    default int priority() {
        return 0;
    }
}
