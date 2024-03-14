package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Allows extension to request the creation of a {@link dev.langchain4j.model.moderation.ModerationModel} bean
 * even if no injection point exists.
 */
public final class RequestModerationModelBeanBuildItem extends MultiBuildItem {

    private final String modelName;

    public RequestModerationModelBeanBuildItem(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }
}
