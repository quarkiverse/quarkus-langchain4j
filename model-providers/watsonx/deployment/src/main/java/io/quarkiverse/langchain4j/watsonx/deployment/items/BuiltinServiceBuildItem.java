package io.quarkiverse.langchain4j.watsonx.deployment.items;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class BuiltinServiceBuildItem extends MultiBuildItem {

    private DotName dotName;

    public BuiltinServiceBuildItem(DotName dotName) {
        this.dotName = dotName;
    }

    public DotName getDotName() {
        return dotName;
    }
}
