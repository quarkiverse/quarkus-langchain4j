package io.quarkiverse.langchain4j.mcp.auth;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

/**
 * MCP client authentication providers can be used to supply credentials such as access tokens, API keys, and other type of
 * credentials.
 * <p>
 * Providers which support a specific named MCP Client only must be annotated with a {@link McpClientName} annotation.
 */
public interface McpClientAuthProvider {

    /**
     * Provide authorization data which will be set as an HTTP Authorization header value.
     *
     * @param input representation of an HTTP request to the model provider.
     * @return authorization data which must include an HTTP Authorization scheme value, for example: "Bearer the_access_token".
     *         Returning null will result in no Authorization header being set.
     */
    String getAuthorization(Input input);

    /*
     * Representation of an HTTP request to the MCP server
     */
    interface Input {
        /*
         * HTTP request method, such as POST or GET
         */
        String method();

        /*
         * HTTP request URI
         */
        URI uri();

        /*
         * HTTP request headers
         */
        Map<String, List<Object>> headers();
    }

    /**
     * Resolve McpClientAuthProvider.
     *
     * @param mcpClientName the MCP client name. If it is not null then a McpClientAuthProvider with a matching
     *        {@link McpClientName}
     *        annotation are preferred to a global McpClientAuthProvider.
     * @return Resolved McpClientAuthProvider as an Optional value which will be empty if no McpClientAuthProvider is available.
     */
    static Optional<McpClientAuthProvider> resolve(String mcpClientName) {
        McpClientAuthProvider authorizer = null;
        // If a model is named then try to find ModelAuthProvider matching this model only
        if (mcpClientName != null) {
            Instance<McpClientAuthProvider> beanInstance = CDI.current().select(McpClientAuthProvider.class,
                    McpClientName.Literal.of(mcpClientName));

            for (var handle : beanInstance.handles()) {
                authorizer = handle.get();
                break;
            }
        }
        // Find a generic McpClientAuthProvider if no MCP client specific McpClientAuthProvider is available
        if (authorizer == null) {
            Instance<McpClientAuthProvider> beanInstance = CDI.current().select(McpClientAuthProvider.class);
            for (var handle : beanInstance.handles()) {
                authorizer = handle.get();
                break;
            }
        }
        return Optional.ofNullable(authorizer);
    }
}
