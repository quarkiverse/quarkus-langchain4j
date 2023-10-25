package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.SimpleBuildItem;

public final class SelectedEmbeddingModelCandidateBuildItem extends SimpleBuildItem {

    private final String provider;

    public SelectedEmbeddingModelCandidateBuildItem(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
