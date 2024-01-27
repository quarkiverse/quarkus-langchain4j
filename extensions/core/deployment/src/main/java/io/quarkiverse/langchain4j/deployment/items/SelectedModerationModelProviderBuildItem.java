package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.SimpleBuildItem;

public final class SelectedModerationModelProviderBuildItem extends SimpleBuildItem {

    private final String provider;

    public SelectedModerationModelProviderBuildItem(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
