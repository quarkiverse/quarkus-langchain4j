package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item that indicates that a certain provider could use a local inference server
 * for a specific LLM embedding model.
 */
public final class DevServicesEmbeddingModelRequiredBuildItem extends MultiBuildItem implements DevServicesModelRequired {

    private final String modelName;
    private final String provider;
    private final String baseUrlProperty;

    public DevServicesEmbeddingModelRequiredBuildItem(String provider, String modelName, String baseUrlProperty) {
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
