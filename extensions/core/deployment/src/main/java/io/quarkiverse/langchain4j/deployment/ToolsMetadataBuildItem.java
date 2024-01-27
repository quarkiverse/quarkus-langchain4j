package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Holds metadata about tools discovered at build time
 */
public final class ToolsMetadataBuildItem extends SimpleBuildItem {

    Map<String, List<ToolMethodCreateInfo>> metadata;

    public ToolsMetadataBuildItem(Map<String, List<ToolMethodCreateInfo>> metadata) {
        this.metadata = metadata;
    }

    public Map<String, List<ToolMethodCreateInfo>> getMetadata() {
        return metadata;
    }
}
