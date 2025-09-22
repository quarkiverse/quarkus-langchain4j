package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface McpRegistryClientRuntimeConfig {

    /**
     * The base URL of the MCP registry, without the API version segment.
     * The default value points at the official registry (https://registry.modelcontextprotocol.io).
     */
    @WithDefault("https://registry.modelcontextprotocol.io")
    String baseUrl();

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
     * The name of the TLS configuration (bucket) that this MCP client registry will use.
     */
    Optional<String> tlsConfigurationName();

    /**
     * The read timeout for the MCP registry's underlying http client
     */
    @ConfigDocDefault("10s")
    @WithDefault("${quarkus.langchain4j.timeout}")
    Optional<Duration> readTimeout();

    /**
     * The connect timeout for the MCP registry's underlying http client
     */
    @ConfigDocDefault("10s")
    @WithDefault("${quarkus.langchain4j.timeout}")
    Optional<Duration> connectTimeout();

}
