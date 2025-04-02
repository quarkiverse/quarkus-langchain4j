package io.quarkiverse.langchain4j.mcp.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface McpClientBuildTimeConfig {

    /**
     * Transport type
     */
    @WithDefault("stdio")
    McpTransportType transportType();
}
