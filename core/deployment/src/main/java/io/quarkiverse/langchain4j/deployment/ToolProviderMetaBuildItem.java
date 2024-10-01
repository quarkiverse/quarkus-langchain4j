package io.quarkiverse.langchain4j.deployment;

import java.util.List;

import io.quarkiverse.langchain4j.deployment.devui.ToolProviderInfo;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Holds metadata about toolProviders discovered at build time
 */
public final class ToolProviderMetaBuildItem extends SimpleBuildItem {
    List<ToolProviderInfo> metadata;

    public ToolProviderMetaBuildItem(List<ToolProviderInfo> metaData) {
        this.metadata = metaData;
    }

    public List<ToolProviderInfo> getMetadata() {
        return metadata;
    }
}
