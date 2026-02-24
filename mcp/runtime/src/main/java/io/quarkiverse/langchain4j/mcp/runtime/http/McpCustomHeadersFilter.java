package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.util.Map;

import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;

/**
 * Hacky way to be able to access the McpCallContext to provide context-aware headers for MCP client.
 */
class McpCustomHeadersFilter implements ResteasyReactiveClientRequestFilter {

    private final McpHeadersSupplier customHeadersSupplier;
    private static final ThreadLocal<McpCallContext> CURRENT_CONTEXT = new ThreadLocal<>();

    McpCustomHeadersFilter(McpHeadersSupplier customHeadersSupplier) {
        this.customHeadersSupplier = customHeadersSupplier;
    }

    static void setCurrentContext(McpCallContext context) {
        CURRENT_CONTEXT.set(context);
    }

    static void clearCurrentContext() {
        CURRENT_CONTEXT.remove();
    }

    @Override
    public void filter(ResteasyReactiveClientRequestContext requestContext) {
        McpCallContext context = CURRENT_CONTEXT.get();
        Map<String, String> headers = customHeadersSupplier.apply(context);
        if (headers != null) {
            headers.forEach((name, value) -> requestContext.getHeaders().putSingle(name, value));
        }
    }
}
