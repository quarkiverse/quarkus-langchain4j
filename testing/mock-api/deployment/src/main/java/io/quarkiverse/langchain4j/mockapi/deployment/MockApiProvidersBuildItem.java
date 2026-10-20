package io.quarkiverse.langchain4j.mockapi.deployment;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpec;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The supported providers on the classpath, known from the candidates every provider's processor announces. Available
 * early in the build, before bean discovery; which of them are in use is {@link MockApiModelsBuildItem}.
 */
public final class MockApiProvidersBuildItem extends SimpleBuildItem {

    private final List<ProviderSpec> providers;

    public MockApiProvidersBuildItem(List<ProviderSpec> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<ProviderSpec> getProviders() {
        return providers;
    }
}
