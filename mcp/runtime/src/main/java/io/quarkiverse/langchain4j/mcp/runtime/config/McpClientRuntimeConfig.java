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
public interface McpClientRuntimeConfig {

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
     * Whether to prefer MicroProfile health checks. Applies to MCP HTTP clients only.
     * <p>
     * If this property is enabled, an HTTP GET call is made to an MCP Server MicroProfile Health endpoint.
     * MicroProfile Health endpoint URL is calculated by extracting a base URL that has no path component
     * from the {@link #url()} property and adding the {@link #microprofileHealthCheckPath()} path to it.
     * <p>
     * Default MCP Client health check that opens a Streamable HTTP or SSE transport channel is attempted
     * when a MicroProfile health check returns an HTTP 404 or other error status.
     */
    @ConfigDocDefault("false")
    @WithDefault("false")
    boolean microprofileHealthCheck();

    /**
     * Relative path of an MCP Server MicroProfile Health endpoint.
     * This property is effective only when the {@link #microprofileHealthCheck()} property is enabled.
     * <p>
     * MicroProfile Health endpoint URL is calculated by extracting the base URL that has no path component
     * from the {@link #url()} property and adding a value of this property to it.
     */
    @ConfigDocDefault("/q/health")
    @WithDefault("/q/health")
    String microprofileHealthCheckPath();

    /**
     * Timeout for tool executions performed by the MCP client
     */
    @WithDefault("${quarkus.langchain4j.timeout:60s}")
    @ConfigDocDefault("60s")
    Duration toolExecutionTimeout();

    /**
     * Timeout for resource-related operations (retrieving a list of resources as well as
     * the actual contents of resources).
     */
    @WithDefault("${quarkus.langchain4j.timeout:60s}")
    @ConfigDocDefault("60s")
    Duration resourcesTimeout();

    /**
     * Timeout for pinging the MCP server process to check if it's still alive. If a ping times out,
     * the client's health check will start failing.
     */
    @WithDefault("10s")
    Duration pingTimeout();

    /**
     * The initial list of MCP roots that the client can present to the server. The list can
     * be later updated programmatically during runtime. The list is formatted as key-value pairs
     * separated by commas. For example:
     * workspace1=/path/to/workspace1,workspace2=/path/to/workspace2
     */
    Optional<List<String>> roots();

    /**
     * The name of the TLS configuration (bucket) used for client authentication in the TLS registry.
     * This does not have any effect when the stdio transport is used.
     */
    Optional<String> tlsConfigurationName();

    /**
     * Static HTTP headers to include in all requests to the MCP server.
     * This only applies to MCP clients using the HTTP or streamable HTTP transport.
     */
    @ConfigDocMapKey("header-name")
    Map<String, String> header();

    /**
     * Whether to cache the tool list obtained from the MCP server.
     * When set to true (the default), the tool list is cached until the server notifies of changes
     * or the cache is manually evicted. When false, the client always fetches a fresh tool list from the server.
     * This is useful when using MCP servers that don't support tool list change notifications.
     */
    Optional<Boolean> cacheToolList();

}
