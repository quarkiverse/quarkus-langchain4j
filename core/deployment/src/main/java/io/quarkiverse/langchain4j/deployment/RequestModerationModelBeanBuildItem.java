package io.quarkiverse.langchain4j.deployment;

import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Allows extension to request the creation of a {@link dev.langchain4j.model.moderation.ModerationModel} bean
 * even if no injection point exists.
 */
public final class RequestModerationModelBeanBuildItem extends MultiBuildItem {

    private final String modelName;

    // TODO: this is in anticipation of actually needing a configurable moderation model
    public RequestModerationModelBeanBuildItem() {
        this.modelName = NamedModelUtil.DEFAULT_NAME;
    }

    public String getModelName() {
        return modelName;
    }
}
