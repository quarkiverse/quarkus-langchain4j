package io.quarkiverse.langchain4j.mcp.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigGroup
public interface McpClientBuildTimeConfig {

    /**
     * Transport type
     */
    @WithDefault("stdio")
    McpTransportType transportType();

    /**
     * Whether metrics are published in case a metrics extension is present.
     */
    @WithName("metrics.enabled")
    @WithDefault("false")
    boolean metricsEnabled();

}
