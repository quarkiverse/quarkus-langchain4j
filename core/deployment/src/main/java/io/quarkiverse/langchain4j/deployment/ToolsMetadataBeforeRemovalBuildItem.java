package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Holds metadata about tools discovered at build time, but tools belonging
 * to beans removed by ArC have not been filtered out yet
 * (ToolsMetadataBuildItem is the build item that already has them filtered
 * out).
 */
public final class ToolsMetadataBeforeRemovalBuildItem extends SimpleBuildItem {

    Map<String, List<ToolMethodCreateInfo>> metadata;

    public ToolsMetadataBeforeRemovalBuildItem(Map<String, List<ToolMethodCreateInfo>> metadata) {
        this.metadata = metadata;
    }

    public Map<String, List<ToolMethodCreateInfo>> getMetadata() {
        return metadata;
    }

}
