package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocIgnore;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.mcp")
public interface McpBuildTimeConfiguration {

    /**
     * Configured MCP clients
     */
    @ConfigDocSection
    @ConfigDocMapKey("client-name")
    @WithParentName
    Map<String, McpClientBuildTimeConfig> clients();

    /**
     * Configured MCP registry clients.
     * This map is only used to capture the configured clients' keys at build time.
     */
    @ConfigDocIgnore
    @WithName("registry-client")
    Map<String, Map<String, String>> registryClients();

    /**
     * Whether the MCP extension should automatically generate a ToolProvider that
     * is wired up to all the configured MCP clients. The default is true if at least
     * one MCP client is configured, false otherwise.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> generateToolProvider();

    /**
     * File containing the MCP servers configuration in the Claude Desktop format.
     * This configuration can only be used to configure {@code stdio} transport type MCP servers.
     * <p>
     * This file is read at <strong>build time</strong> which means that which MCP servers the client will use,
     * is determined at build time. However, specific configuration of each MCP server can be overridden at runtime.
     */
    Optional<String> configFile();

    /**
     * Whether the MCP extension should automatically register a health check for configured MCP clients.
     * The default is true if at least one MCP client is configured, false otherwise.
     */
    @WithName("health.enabled")
    @WithDefault("true")
    boolean mpHealthEnabled();
}
