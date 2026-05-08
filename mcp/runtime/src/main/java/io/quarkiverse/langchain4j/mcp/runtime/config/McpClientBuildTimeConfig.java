package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigGroup
public interface McpClientBuildTimeConfig {

    /**
     * Whether this MCP client is enabled.
     */
    @WithDefault("true")
    boolean enabled();

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

    /**
     * The name of an OIDC client configured via {@code quarkus.oidc-client.<name>.*} used by the
     * {@code langchain4j-oidc-client-mcp-auth-provider} extension to acquire access tokens
     * (e.g. via the {@code client_credentials} grant) for this MCP client.
     * Has no effect when that extension is not on the classpath.
     */
    Optional<String> oidcClientName();

}
