package io.quarkiverse.langchain4j.mockapi.internal.config;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Serves the endpoint overrides of the models in use above every other source, so the mock URL wins over a URL the
 * user configured for the real provider.
 */
public final class MockApiEndpointConfigSource implements ConfigSource {

    public static final int ORDINAL = Integer.MAX_VALUE - 1500;

    private final Map<String, String> overrides;

    public MockApiEndpointConfigSource(Map<String, String> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public Set<String> getPropertyNames() {
        return overrides.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return overrides.get(propertyName);
    }

    @Override
    public String getName() {
        return "MockModelApi";
    }
}
