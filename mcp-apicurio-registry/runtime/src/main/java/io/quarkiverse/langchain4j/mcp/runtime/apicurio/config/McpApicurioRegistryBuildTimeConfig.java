package io.quarkiverse.langchain4j.mcp.runtime.apicurio.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.mcp.apicurio-registry")
public interface McpApicurioRegistryBuildTimeConfig {

    /**
     * Whether the Apicurio Registry MCP integration is enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Whether metrics are collected for the MCP clients of the servers discovered in the registry,
     * like {@code quarkus.langchain4j.mcp."client-name".metrics.enabled} does for a configured client.
     * Only applies when Micrometer is present.
     */
    @WithName("metrics.enabled")
    @WithDefault("false")
    boolean metricsEnabled();
}
