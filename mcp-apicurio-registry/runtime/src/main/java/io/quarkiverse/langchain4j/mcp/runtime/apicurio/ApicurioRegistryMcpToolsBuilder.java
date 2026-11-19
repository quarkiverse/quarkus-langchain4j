package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import java.util.Objects;

import dev.langchain4j.mcp.McpToolProvider;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;

/**
 * Builder for creating {@link ApicurioRegistryMcpTools} instances programmatically,
 * for use cases where CDI is not available (e.g. dynamically created tool providers).
 */
public class ApicurioRegistryMcpToolsBuilder {

    private String registryUrl;
    private String username;
    private String password;
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private McpToolProvider toolProvider;

    public static ApicurioRegistryMcpToolsBuilder create() {
        return new ApicurioRegistryMcpToolsBuilder();
    }

    public ApicurioRegistryMcpToolsBuilder registryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
        return this;
    }

    public ApicurioRegistryMcpToolsBuilder basicAuth(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public ApicurioRegistryMcpToolsBuilder oauth2(String tokenEndpoint, String clientId, String clientSecret) {
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        return this;
    }

    public ApicurioRegistryMcpToolsBuilder toolProvider(McpToolProvider toolProvider) {
        this.toolProvider = toolProvider;
        return this;
    }

    /**
     * @deprecated The MCP clients no longer run on a Vert.x instance supplied here, so it is ignored.
     */
    @Deprecated(forRemoval = true)
    public ApicurioRegistryMcpToolsBuilder vertx(Vertx vertx) {
        return this;
    }

    public ApicurioRegistryMcpTools build() {
        Objects.requireNonNull(registryUrl, "registryUrl is required");
        Objects.requireNonNull(toolProvider, "toolProvider is required");

        RegistryClientOptions options = RegistryClientOptions.create(registryUrl);
        if (username != null && password != null) {
            options.basicAuth(username, password);
        } else if (tokenEndpoint != null && clientId != null && clientSecret != null) {
            options.oauth2(tokenEndpoint, clientId, clientSecret);
        }
        RegistryClient client = RegistryClientFactory.create(options);

        return new ApicurioRegistryMcpTools(client, toolProvider);
    }
}
