package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class SelectedModerationModelProviderBuildItem extends MultiBuildItem {

    private final String provider;
    private final String modelName;

    public SelectedModerationModelProviderBuildItem(String provider, String modelName) {
        this.provider = provider;
        this.modelName = modelName;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }
}
