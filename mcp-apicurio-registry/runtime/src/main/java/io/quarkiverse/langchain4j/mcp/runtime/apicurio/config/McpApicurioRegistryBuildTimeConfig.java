package io.quarkiverse.langchain4j.mcp.runtime.apicurio.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.mcp.apicurio-registry")
public interface McpApicurioRegistryBuildTimeConfig {

    /**
     * Whether the Apicurio Registry MCP integration is enabled.
     */
    @WithDefault("true")
    boolean enabled();
}
