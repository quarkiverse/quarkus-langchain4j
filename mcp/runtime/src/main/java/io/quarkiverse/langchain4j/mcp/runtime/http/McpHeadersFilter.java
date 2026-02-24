package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.util.Map;

import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;

import dev.langchain4j.mcp.client.McpHeadersSupplier;

/**
 * A JAX-RS client request filter that adds HTTP headers to outgoing requests based on a provided
 * {@link McpHeadersSupplier}.
 */
class McpHeadersFilter implements ResteasyReactiveClientRequestFilter {

    private final McpHeadersSupplier headersSupplier;

    McpHeadersFilter(McpHeadersSupplier headersSupplier) {
        this.headersSupplier = headersSupplier;
    }

    @Override
    public void filter(ResteasyReactiveClientRequestContext requestContext) {
        Map<String, String> headers = headersSupplier.apply(null);
        if (headers != null) {
            headers.forEach((name, value) -> requestContext.getHeaders().putSingle(name, value));
        }
    }
}
