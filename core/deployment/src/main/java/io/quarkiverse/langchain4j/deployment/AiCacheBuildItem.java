package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

public final class AiCacheBuildItem extends SimpleBuildItem {

    private boolean enable;
    private String embeddingModelName;

    public AiCacheBuildItem(boolean enable, String embeddingModelName) {
        this.enable = enable;
        this.embeddingModelName = embeddingModelName;
    }

    public boolean isEnable() {
        return enable;
    }

    public String getEmbeddingModelName() {
        return embeddingModelName;
    }
}
