package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Allows extension to request the creation of a {@link dev.langchain4j.model.moderation.ModerationModel} bean
 * even if no injection point exists.
 */
public final class RequestModerationModelBeanBuildItem extends MultiBuildItem {

    private final String configName;

    public RequestModerationModelBeanBuildItem(String configName) {
        this.configName = configName;
    }

    public String getConfigName() {
        return configName;
    }
}
