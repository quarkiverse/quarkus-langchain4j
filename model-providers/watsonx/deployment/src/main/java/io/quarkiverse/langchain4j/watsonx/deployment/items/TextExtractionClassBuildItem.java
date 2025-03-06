package io.quarkiverse.langchain4j.watsonx.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class TextExtractionClassBuildItem extends MultiBuildItem {

    private String qualifier;

    public TextExtractionClassBuildItem(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getQualifier() {
        return qualifier;
    }
}
