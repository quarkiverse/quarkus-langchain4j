package io.quarkiverse.langchain4j.deployment;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.microprofile.config.ConfigProvider;

import io.smallrye.config.ConfigMappings;
import io.smallrye.config.ConfigMappings.ConfigClass;
import io.smallrye.config.NameIterator;

/**
 * Discovers the keys of a named configuration map, such as the named embedding stores of an extension.
 * <p>
 * Extensions declare their named configurations as {@code @WithParentName @WithDefaults Map<String, G>}, which SmallRye
 * Config only materializes keys for when a configured property structurally matches the config group {@code G}. A
 * build-time group usually holds a couple of properties at most, so a named entry configured through runtime properties
 * alone never produces a key, and the corresponding beans are never created.
 * <p>
 * This scans the configured property names instead, treating any {@code <prefix>.<name>.<property>} property as a named
 * entry unless {@code <name>} is a property of the unnamed configuration.
 */
public final class NamedConfigDiscovery {

    private NamedConfigDiscovery() {
    }

    /**
     * Discovers the named configuration keys from the properties of the current {@code Config}.
     *
     * @param configPrefix the config prefix the named configurations live under, without a trailing dot,
     *        for instance {@code quarkus.langchain4j.pgvector}
     * @param knownNames the names SmallRye Config already materialized, kept first in the returned set
     * @param mappingClasses the config mapping roots covering the configuration, across all config phases
     */
    public static Set<String> discoverNames(String configPrefix, Set<String> knownNames, Class<?>... mappingClasses) {
        return discoverNames(ConfigProvider.getConfig().getPropertyNames(), configPrefix, knownNames, mappingClasses);
    }

    /**
     * Discovers the named configuration keys from the given property names.
     */
    public static Set<String> discoverNames(Iterable<String> propertyNames, String configPrefix, Set<String> knownNames,
            Class<?>... mappingClasses) {
        Set<String> unnamedProperties = unnamedPropertyNames(configPrefix, mappingClasses);

        Set<String> names = new LinkedHashSet<>(knownNames);
        for (String propertyName : propertyNames) {
            if (!propertyName.startsWith(configPrefix + ".")) {
                continue;
            }

            NameIterator name = new NameIterator(propertyName, configPrefix.length());
            String segment = name.getNextSegment();
            name.next();

            if (name.hasNext() && !unnamedProperties.contains(withoutIndex(segment))) {
                names.add(segment);
            }
        }

        return names;
    }

    /**
     * Collects the first path segment of every property belonging to the unnamed configuration, as opposed to the
     * {@code <prefix>.*.<property>} ones that belong to a named entry. Letting SmallRye Config expand the mappings
     * keeps the naming rules ({@code @WithName}, {@code @WithParentName}, nested groups, indexed collections) in one
     * place instead of reimplementing them here.
     */
    private static Set<String> unnamedPropertyNames(String configPrefix, Class<?>... mappingClasses) {
        Set<String> unnamedProperties = new HashSet<>();
        for (Class<?> mappingClass : mappingClasses) {
            for (String path : ConfigMappings.getProperties(ConfigClass.configClass(mappingClass, configPrefix)).keySet()) {
                if (!path.startsWith(configPrefix + ".")) {
                    continue;
                }

                String segment = new NameIterator(path, configPrefix.length()).getNextSegment();
                if (!"*".equals(segment)) {
                    unnamedProperties.add(withoutIndex(segment));
                }
            }
        }
        return unnamedProperties;
    }

    /**
     * Strips the collection index off a path segment, so that a configured {@code metadata-indexes[0]} is recognized as
     * the mapped {@code metadata-indexes[*]} property.
     */
    private static String withoutIndex(String segment) {
        int index = segment.indexOf('[');
        return index == -1 ? segment : segment.substring(0, index);
    }
}
