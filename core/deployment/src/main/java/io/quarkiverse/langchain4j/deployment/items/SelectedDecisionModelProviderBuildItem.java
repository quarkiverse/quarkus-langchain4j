package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class SelectedDecisionModelProviderBuildItem extends MultiBuildItem {

    private final String provider;
    private final String configName;

    public SelectedDecisionModelProviderBuildItem(String provider, String configName) {
        this.provider = provider;
        this.configName = configName;
    }

    public String getProvider() {
        return provider;
    }

    public String getConfigName() {
        return configName;
    }
}
