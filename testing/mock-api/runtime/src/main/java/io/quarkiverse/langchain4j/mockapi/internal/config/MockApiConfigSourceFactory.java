package io.quarkiverse.langchain4j.mockapi.internal.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;

/**
 * Turns the {@code quarkus.langchain4j.mock-api.overrides} the dev service published into a config source.
 */
public final class MockApiConfigSourceFactory implements ConfigSourceFactory {

    public static final String OVERRIDES = "quarkus.langchain4j.mock-api.overrides";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        ConfigValue value = context.getValue(OVERRIDES);
        if (value == null || value.getValue() == null || value.getValue().isBlank()) {
            return List.of();
        }
        return List.of(new MockApiEndpointConfigSource(parse(value.getValue())));
    }

    /**
     * {@code key=url} pairs separated by commas, as the dev service publishes them.
     */
    public static Map<String, String> parse(String overrides) {
        Map<String, String> result = new HashMap<>();
        for (String pair : overrides.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return result;
    }

    public static String format(Map<String, String> overrides) {
        return overrides.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted()
                .collect(Collectors.joining(","));
    }
}
