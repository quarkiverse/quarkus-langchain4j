package io.quarkiverse.langchain4j.watsonx.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class WatsonxChatModelProviderBuildItem extends MultiBuildItem {

    private final String configName;
    private final String mode;

    public WatsonxChatModelProviderBuildItem(String configName, String mode) {
        this.configName = configName;
        this.mode = mode;
    }

    public String getConfigName() {
        return configName;
    }

    public String getMode() {
        return mode;
    }
}
