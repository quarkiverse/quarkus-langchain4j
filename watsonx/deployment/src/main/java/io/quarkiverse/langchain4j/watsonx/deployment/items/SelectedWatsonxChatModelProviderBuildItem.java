package io.quarkiverse.langchain4j.watsonx.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class SelectedWatsonxChatModelProviderBuildItem extends MultiBuildItem {

    private final String provider;
    private final String modelName;
    private final String deployment;

    public SelectedWatsonxChatModelProviderBuildItem(String provider, String modelName, String deployment) {
        this.provider = provider;
        this.modelName = modelName;
        this.deployment = deployment;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getDeployment() {
        return deployment;
    }
}
