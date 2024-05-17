package io.quarkiverse.langchain4j.deployment.devui;

import java.util.Collections;
import java.util.Map;

import io.quarkus.builder.item.MultiBuildItem;

public final class AdditionalDevUiCardBuildItem extends MultiBuildItem {

    private final String title;
    private final String icon;
    private final String componentLink;
    private final Map<String, Object> buildTimeData;

    public AdditionalDevUiCardBuildItem(String title, String icon, String componentLink) {
        this(title, icon, componentLink, Collections.emptyMap());
    }

    public AdditionalDevUiCardBuildItem(String title, String icon, String componentLink, Map<String, Object> buildTimeData) {
        this.title = title;
        this.icon = icon;
        this.componentLink = componentLink;
        this.buildTimeData = buildTimeData;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    public String getComponentLink() {
        return componentLink;
    }

    public Map<String, Object> getBuildTimeData() {
        return buildTimeData;
    }
}
