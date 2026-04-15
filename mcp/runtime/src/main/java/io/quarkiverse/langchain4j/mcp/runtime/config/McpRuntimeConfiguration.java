package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.mcp")
public interface McpRuntimeConfiguration {

    /**
     * Configured MCP clients
     */
    @ConfigDocSection
    @ConfigDocMapKey("client-name")
    @WithParentName
    Map<String, McpClientRuntimeConfig> clients();

    /**
     * Configured MCP registry clients
     */
    @ConfigDocSection
    @ConfigDocMapKey("registry-client-name")
    @WithName("registry-client")
    Map<String, McpRegistryClientRuntimeConfig> registryClients();

    /**
     * Whether resources should be exposed as MCP tools.
     */
    @WithDefault("false")
    Optional<Boolean> exposeResourcesAsTools();

    /**
     * Apicurio Registry configuration for dynamic MCP server discovery.
     * When configured, provides tools that allow the LLM to search for
     * and connect to MCP servers registered in an Apicurio Registry instance.
     */
    @ConfigDocSection
    @WithName("apicurio-registry")
    Optional<ApicurioRegistryConfig> apicurioRegistry();
}
