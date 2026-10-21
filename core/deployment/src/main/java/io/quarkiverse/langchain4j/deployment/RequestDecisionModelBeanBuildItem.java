package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Allows extension to request the creation of a {@link dev.langchain4j.model.decision.DecisionModel} bean
 * even if no injection point exists.
 */
public final class RequestDecisionModelBeanBuildItem extends MultiBuildItem {

    private final String configName;

    public RequestDecisionModelBeanBuildItem(String configName) {
        this.configName = configName;
    }

    public String getConfigName() {
        return configName;
    }
}
