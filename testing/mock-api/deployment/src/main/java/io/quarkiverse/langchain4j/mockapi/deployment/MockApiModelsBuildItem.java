package io.quarkiverse.langchain4j.mockapi.deployment;

import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpec;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The models to point at the mock, by provider: the unnamed model as {@code <default>}, always, and every named model an
 * injection point or AI service refers to. Core knows the named ones only once bean discovery has finished, so a step
 * consuming this item runs after discovery and cannot register beans other than synthetic ones.
 */
public final class MockApiModelsBuildItem extends SimpleBuildItem {

    private final List<ProviderSpec> providers;
    private final Map<String, List<String>> configNamesByProvider;

    public MockApiModelsBuildItem(List<ProviderSpec> providers, Map<String, List<String>> configNamesByProvider) {
        this.providers = List.copyOf(providers);
        this.configNamesByProvider = Map.copyOf(configNamesByProvider);
    }

    public List<ProviderSpec> getProviders() {
        return providers;
    }

    public List<String> getConfigNames(ProviderSpec spec) {
        return configNamesByProvider.getOrDefault(spec.id(), List.of());
    }

    public Map<String, List<String>> getConfigNamesByProvider() {
        return configNamesByProvider;
    }
}
