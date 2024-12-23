package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface McpClientConfig {

    /**
     * Transport type
     */
    McpTransportType transportType();

    /**
     * The URL of the SSE endpoint. This only applies to MCP clients using the HTTP transport.
     */
    Optional<String> url();

    /**
     * The command to execute to spawn the MCP server process. This only applies to MCP clients
     * using the STDIO transport.
     */
    Optional<List<String>> command();

    /**
     * Environment variables for the spawned MCP server process. This only applies to MCP clients
     * using the STDIO transport.
     */
    @ConfigDocMapKey("env-var")
    Map<String, String> environment();

    /**
     * Whether to log requests
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether to log responses
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-responses}")
    Optional<Boolean> logResponses();

    /**
     * Timeout for tool executions performed by the MCP client
     */
    @WithDefault("60s")
    Duration toolExecutionTimeout();
}
