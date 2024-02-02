package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class SelectedChatModelProviderBuildItem extends MultiBuildItem {

    private final String provider;
    private final String modelName;

    public SelectedChatModelProviderBuildItem(String provider, String modelName) {
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
