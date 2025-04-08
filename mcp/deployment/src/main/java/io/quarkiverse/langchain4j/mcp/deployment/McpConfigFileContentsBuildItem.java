package io.quarkiverse.langchain4j.mcp.deployment;

import java.util.Map;

import io.quarkiverse.langchain4j.mcp.runtime.config.LocalLaunchParams;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Contains the parsed contents of a Claude Desktop config file
 */
public final class McpConfigFileContentsBuildItem extends SimpleBuildItem {

    private final Map<String, LocalLaunchParams> contents;

    public McpConfigFileContentsBuildItem(Map<String, LocalLaunchParams> contents) {
        this.contents = contents;
    }

    public Map<String, LocalLaunchParams> getContents() {
        return contents;
    }
}
