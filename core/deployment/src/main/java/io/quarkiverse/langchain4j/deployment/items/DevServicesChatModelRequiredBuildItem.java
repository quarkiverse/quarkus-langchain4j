package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item that indicates that a certain provider could use a local inference server
 * for a specific LLM chat model.
 */
public final class DevServicesChatModelRequiredBuildItem extends MultiBuildItem implements DevServicesModelRequired {

    private final String modelName;
    private final String provider;
    private final String baseUrlProperty;

    public DevServicesChatModelRequiredBuildItem(String provider, String modelName, String baseUrlProperty) {
        this.modelName = modelName;
        this.provider = provider;
        this.baseUrlProperty = baseUrlProperty;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getBaseUrlProperty() {
        return baseUrlProperty;
    }
}
