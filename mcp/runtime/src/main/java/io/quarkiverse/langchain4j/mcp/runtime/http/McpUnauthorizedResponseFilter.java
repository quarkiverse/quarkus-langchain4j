package io.quarkiverse.langchain4j.mcp.runtime.http;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.client.impl.PreservesThreadClientResponseFilter;

import io.quarkiverse.langchain4j.mcp.auth.McpAuthenticationException;

/**
 * Reports a rejected credential as a {@link McpAuthenticationException}, which carries the resource metadata from the
 * {@code WWW-Authenticate} header so that the application can start an authorization flow. The Streamable HTTP
 * transport would otherwise only report the status code.
 */
class McpUnauthorizedResponseFilter implements PreservesThreadClientResponseFilter {

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        if (responseContext.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()) {
            throw new McpAuthenticationException(responseContext.getStatus(),
                    responseContext.getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
        }
    }
}
