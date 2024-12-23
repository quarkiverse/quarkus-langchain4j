package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.mcp")
public interface McpConfiguration {

    /**
     * Configured MCP clients
     */
    @ConfigDocSection
    @ConfigDocMapKey("client-name")
    @WithParentName
    Map<String, McpClientConfig> clients();

    /**
     * Whether the MCP extension should automatically generate a ToolProvider that
     * is wired up to all the configured MCP clients. The default is true if at least
     * one MCP client is configured, false otherwise.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> generateToolProvider();

}
