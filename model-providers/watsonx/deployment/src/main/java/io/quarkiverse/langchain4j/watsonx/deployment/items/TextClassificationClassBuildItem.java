package io.quarkiverse.langchain4j.watsonx.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class TextClassificationClassBuildItem extends MultiBuildItem {

    private String qualifier;

    public TextClassificationClassBuildItem(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getQualifier() {
        return qualifier;
    }
}
