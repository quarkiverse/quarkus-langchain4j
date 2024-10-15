package io.quarkiverse.langchain4j.watsonx.deployment.items;

import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;
import io.quarkus.builder.item.MultiBuildItem;

public final class WatsonxChatModelProviderBuildItem extends MultiBuildItem {

    private final String configName;
    private final String mode;
    private final PromptFormatter promptFormatter;

    public WatsonxChatModelProviderBuildItem(String configName, String mode, PromptFormatter promptTemplate) {
        this.configName = configName;
        this.mode = mode;
        this.promptFormatter = promptTemplate;
    }

    public String getConfigName() {
        return configName;
    }

    public String getMode() {
        return mode;
    }

    public PromptFormatter getPromptFormatter() {
        return promptFormatter;
    }
}
