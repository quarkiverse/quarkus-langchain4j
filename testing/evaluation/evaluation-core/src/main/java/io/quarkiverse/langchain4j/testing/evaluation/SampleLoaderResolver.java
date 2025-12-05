package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Resolver for SampleLoader implementations using a hybrid CDI/ServiceLoader approach.
 * <p>
 * This resolver discovers loaders from both CDI and ServiceLoader sources:
 * </p>
 * <ul>
 * <li>CDI beans (when running in Quarkus runtime or @QuarkusTest context) - supports dependency injection</li>
 * <li>ServiceLoader registrations (always checked) - ensures loaders from dependencies are available</li>
 * </ul>
 * <p>
 * When a loader is found via both sources, the CDI version is preferred as it may
 * have dependency injection support. This hybrid approach ensures maximum compatibility
 * across different runtime environments.
 * </p>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * // Works in both plain JUnit and @QuarkusTest contexts
 * Samples<String> samples = SampleLoaderResolver.load("samples.yaml", String.class);
 * }</pre>
 */
public class SampleLoaderResolver {

    private SampleLoaderResolver() {
        // Utility class
    }

    /**
     * Load samples from the given source using the best available loader.
     * <p>
     * This method:
     * </p>
     * <ol>
     * <li>Discovers loaders from both CDI and ServiceLoader</li>
     * <li>Filters loaders that support the given source</li>
     * <li>Selects the loader with highest priority</li>
     * <li>Uses the selected loader to load samples</li>
     * </ol>
     *
     * @param source source identifier (file path, URL, etc.)
     * @param outputType the expected output type class
     * @param <T> the type of expected output
     * @return loaded samples
     * @throws SampleLoadException if no loader supports the source or loading fails
     */
    public static <T> Samples<T> load(String source, Class<T> outputType) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Source must not be null or blank");
        }
        if (outputType == null) {
            throw new IllegalArgumentException("Output type must not be null");
        }

        List<SampleLoader<?>> loaders = discoverLoaders();

        if (loaders.isEmpty()) {
            throw new SampleLoadException(
                    "No SampleLoader implementations found. " +
                            "Ensure loaders are registered in META-INF/services " +
                            "or available as CDI beans.");
        }

        // Find all loaders that support this source
        List<SampleLoader<?>> supportedLoaders = loaders.stream()
                .filter(loader -> loader.supports(source))
                .sorted((l1, l2) -> Integer.compare(l2.priority(), l1.priority()))
                .toList();

        if (supportedLoaders.isEmpty()) {
            throw new SampleLoadException(
                    String.format("No SampleLoader found supporting source: %s%n" +
                            "Available loaders: %s",
                            source,
                            loaders.stream()
                                    .map(l -> l.getClass().getSimpleName())
                                    .toList()));
        }

        // Use the highest priority loader
        SampleLoader<?> selectedLoader = supportedLoaders.get(0);
        try {
            @SuppressWarnings("unchecked")
            SampleLoader<T> typedLoader = (SampleLoader<T>) selectedLoader;
            return typedLoader.load(source, outputType);
        } catch (SampleLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SampleLoadException(
                    String.format("Failed to load samples from %s using %s",
                            source, selectedLoader.getClass().getSimpleName()),
                    e);
        }
    }

    /**
     * Discover available SampleLoader implementations.
     * <p>
     * Uses a hybrid approach that combines both CDI and ServiceLoader discovery:
     * </p>
     * <ul>
     * <li>If CDI is available, discovers loaders via CDI (which supports dependency injection)</li>
     * <li>Always discovers loaders via ServiceLoader (works in all contexts)</li>
     * <li>Merges both sources, removing duplicates based on class identity</li>
     * </ul>
     * <p>
     * This ensures that loaders registered via ServiceLoader are always available,
     * even in CDI contexts where dependency JARs may not be indexed for bean discovery.
     * </p>
     *
     * @return list of discovered loaders
     */
    private static List<SampleLoader<?>> discoverLoaders() {
        List<SampleLoader<?>> loaders = new ArrayList<>();

        // Discover via CDI if available (supports dependency injection)
        if (isCDIAvailable()) {
            loaders.addAll(discoverViaCDI());
        }

        // Always discover via ServiceLoader (works in all contexts)
        loaders.addAll(discoverViaServiceLoader());

        // Remove duplicates based on class identity
        return deduplicateLoaders(loaders);
    }

    /**
     * Remove duplicate loaders based on class identity.
     * <p>
     * When the same loader class is found via both CDI and ServiceLoader,
     * prefer the CDI version (which may have dependency injection support).
     * </p>
     *
     * @param loaders list of loaders to deduplicate
     * @return deduplicated list
     */
    private static List<SampleLoader<?>> deduplicateLoaders(List<SampleLoader<?>> loaders) {
        List<SampleLoader<?>> deduplicated = new ArrayList<>();
        List<Class<?>> seenClasses = new ArrayList<>();

        for (SampleLoader<?> loader : loaders) {
            Class<?> loaderClass = loader.getClass();
            // For CDI proxies, get the actual class
            if (loaderClass.getName().contains("_ClientProxy")) {
                // CDI proxy - extract the actual class from the proxy
                Class<?>[] interfaces = loaderClass.getInterfaces();
                if (interfaces.length > 0) {
                    loaderClass = interfaces[0];
                }
            }

            if (!seenClasses.contains(loaderClass)) {
                seenClasses.add(loaderClass);
                deduplicated.add(loader);
            }
        }

        return deduplicated;
    }

    /**
     * Check if CDI is available in the current context.
     *
     * @return true if CDI container is available
     */
    private static boolean isCDIAvailable() {
        try {
            CDI.current();
            return true;
        } catch (IllegalStateException e) {
            // CDI container not available
            return false;
        }
    }

    /**
     * Discover loaders via CDI.
     * <p>
     * Used when running in Quarkus runtime or @QuarkusTest context.
     * </p>
     *
     * @return list of CDI-managed loaders
     */
    @SuppressWarnings("rawtypes")
    private static List<SampleLoader<?>> discoverViaCDI() {
        List<SampleLoader<?>> loaders = new ArrayList<>();

        try {
            Instance<SampleLoader> instances = CDI.current()
                    .select(SampleLoader.class);

            for (SampleLoader<?> loader : instances) {
                loaders.add(loader);
            }
        } catch (Exception e) {
            throw new SampleLoadException("Failed to discover SampleLoaders via CDI", e);
        }

        return loaders;
    }

    /**
     * Discover loaders via Java ServiceLoader.
     * <p>
     * Used when running in plain JUnit tests without CDI.
     * </p>
     *
     * @return list of ServiceLoader-discovered loaders
     */
    @SuppressWarnings("rawtypes")
    private static List<SampleLoader<?>> discoverViaServiceLoader() {
        List<SampleLoader<?>> loaders = new ArrayList<>();

        ServiceLoader<SampleLoader> serviceLoader = ServiceLoader.load(SampleLoader.class);
        for (SampleLoader<?> loader : serviceLoader) {
            loaders.add(loader);
        }

        return loaders;
    }
}
