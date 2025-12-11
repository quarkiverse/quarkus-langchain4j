package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Registry for discovering report formatters using a hybrid CDI/ServiceLoader approach.
 * <p>
 * This registry discovers formatters from both CDI and ServiceLoader sources:
 * </p>
 * <ul>
 * <li>CDI beans (when running in Quarkus runtime or @QuarkusTest context) - supports dependency injection</li>
 * <li>ServiceLoader registrations (always checked) - ensures formatters from dependencies are available</li>
 * </ul>
 * <p>
 * When a formatter is found via both sources, the CDI version is preferred as it may
 * have dependency injection support. This hybrid approach ensures maximum compatibility
 * across different runtime environments.
 * </p>
 */
public class ReportFormatterRegistry {

    private ReportFormatterRegistry() {
        // Utility class
    }

    /**
     * Get a report formatter for the specified format.
     *
     * @param format the format identifier (e.g., "markdown", "json", "html")
     * @return the report formatter
     * @throws IllegalArgumentException if no formatter found for the format
     */
    public static ReportFormatter get(String format) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Format must not be null or blank");
        }

        List<ReportFormatter> formatters = discoverFormatters();

        return formatters.stream()
                .filter(f -> f.format().equalsIgnoreCase(format))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("No ReportFormatter found for format: %s%nAvailable formats: %s",
                                format,
                                formatters.stream()
                                        .map(ReportFormatter::format)
                                        .distinct()
                                        .sorted()
                                        .collect(Collectors.toList()))));
    }

    /**
     * Get all supported report formats.
     *
     * @return set of format identifiers (e.g., ["markdown", "json", "html"])
     */
    public static Set<String> supportedFormats() {
        return discoverFormatters().stream()
                .map(ReportFormatter::format)
                .collect(Collectors.toSet());
    }

    /**
     * Discover available ReportFormatter implementations.
     * <p>
     * Uses a hybrid approach that combines both CDI and ServiceLoader discovery:
     * </p>
     * <ul>
     * <li>If CDI is available, discovers formatters via CDI (which supports dependency injection)</li>
     * <li>Always discovers formatters via ServiceLoader (works in all contexts)</li>
     * <li>Merges both sources, removing duplicates based on class identity</li>
     * </ul>
     * <p>
     * This ensures that formatters registered via ServiceLoader are always available,
     * even in CDI contexts where dependency JARs may not be indexed for bean discovery.
     * </p>
     *
     * @return list of discovered formatters sorted by priority (highest first)
     */
    private static List<ReportFormatter> discoverFormatters() {
        List<ReportFormatter> formatters = new ArrayList<>();

        // Discover via CDI if available (supports dependency injection)
        if (isCDIAvailable()) {
            formatters.addAll(discoverViaCDI());
        }

        // Always discover via ServiceLoader (works in all contexts)
        formatters.addAll(discoverViaServiceLoader());

        // Remove duplicates based on class identity
        List<ReportFormatter> deduplicated = deduplicateFormatters(formatters);

        // Sort by priority (highest first)
        deduplicated.sort((f1, f2) -> Integer.compare(f2.priority(), f1.priority()));

        return deduplicated;
    }

    /**
     * Discover formatters via CDI.
     *
     * @return list of formatters discovered via CDI
     */
    private static List<ReportFormatter> discoverViaCDI() {
        try {
            Instance<ReportFormatter> instance = CDI.current().select(ReportFormatter.class);
            return instance.stream().toList();
        } catch (IllegalStateException e) {
            // CDI not available or not properly initialized
            return List.of();
        }
    }

    /**
     * Discover formatters via ServiceLoader.
     *
     * @return list of formatters discovered via ServiceLoader
     */
    private static List<ReportFormatter> discoverViaServiceLoader() {
        return ServiceLoader.load(ReportFormatter.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    /**
     * Check if CDI is available in the current context.
     *
     * @return true if CDI is available
     */
    private static boolean isCDIAvailable() {
        try {
            CDI.current();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Remove duplicate formatters based on class identity.
     * <p>
     * When the same formatter class is found via both CDI and ServiceLoader,
     * prefer the CDI version (which may have dependency injection support).
     * </p>
     *
     * @param formatters list of formatters to deduplicate
     * @return deduplicated list
     */
    private static List<ReportFormatter> deduplicateFormatters(List<ReportFormatter> formatters) {
        List<ReportFormatter> deduplicated = new ArrayList<>();
        List<Class<?>> seenClasses = new ArrayList<>();

        for (ReportFormatter formatter : formatters) {
            Class<?> formatterClass = formatter.getClass();
            // For CDI proxies, get the actual class
            if (formatterClass.getName().contains("_ClientProxy")) {
                // CDI proxy - extract the actual class from the proxy
                Class<?>[] interfaces = formatterClass.getInterfaces();
                if (interfaces.length > 0) {
                    formatterClass = interfaces[0];
                }
            }

            if (!seenClasses.contains(formatterClass)) {
                seenClasses.add(formatterClass);
                deduplicated.add(formatter);
            }
        }

        return deduplicated;
    }
}
