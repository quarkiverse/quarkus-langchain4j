package io.quarkiverse.langchain4j.mcp.runtime.http;

import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;

/**
 * Creates the HTTP clients used by the {@link StreamableHttpMcpTransport} in Quarkus. They run on the Quarkus REST
 * client, which also provides the TLS and proxy configuration, and add the Quarkus MCP authentication support: the
 * {@code Authorization} header is set from a {@link McpClientAuthProvider}, and a rejected credential is reported as a
 * {@link io.quarkiverse.langchain4j.mcp.auth.McpAuthenticationException}.
 * <p>
 * Pass the builder to {@link StreamableHttpMcpTransport.Builder#httpClientBuilder}.
 */
public final class McpHttpClients {

    private McpHttpClients() {
    }

    /**
     * Creates a builder that authenticates with the {@link McpClientAuthProvider} bean for the named MCP client,
     * if there is one, or else with the global {@link McpClientAuthProvider} bean, if there is one.
     */
    public static JaxRsHttpClientBuilder builder(String mcpClientName) {
        return builder(McpClientAuthProvider.resolve(mcpClientName).orElse(null));
    }

    /**
     * Creates a builder that authenticates with the given provider, or not at all if it is {@code null}.
     */
    public static JaxRsHttpClientBuilder builder(McpClientAuthProvider authProvider) {
        JaxRsHttpClientBuilder builder = new JaxRsHttpClientBuilder();
        if (authProvider != null) {
            builder.addClientProvider(new McpClientAuthFilter(authProvider));
        }
        builder.addClientProvider(new McpUnauthorizedResponseFilter());
        return builder;
    }
}
