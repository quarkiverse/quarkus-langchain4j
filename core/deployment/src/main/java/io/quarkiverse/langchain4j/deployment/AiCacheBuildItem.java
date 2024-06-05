package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

public final class AiCacheBuildItem extends SimpleBuildItem {

    private boolean enable;

    public AiCacheBuildItem(boolean enable) {
        this.enable = enable;
    }

    public boolean isEnable() {
        return enable;
    }
}
