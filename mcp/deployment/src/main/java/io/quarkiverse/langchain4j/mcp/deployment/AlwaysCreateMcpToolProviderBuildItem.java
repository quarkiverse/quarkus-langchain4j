package io.quarkiverse.langchain4j.mcp.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * When produced, signals the MCP processor to always create the MCP tool provider
 * synthetic bean, even when no static MCP clients are configured. This is needed
 * by extensions that dynamically add MCP clients at runtime (e.g. Apicurio Registry).
 */
public final class AlwaysCreateMcpToolProviderBuildItem extends SimpleBuildItem {
}
