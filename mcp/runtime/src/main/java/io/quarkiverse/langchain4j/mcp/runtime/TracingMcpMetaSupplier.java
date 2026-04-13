package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.Collections;
import java.util.Map;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpMetaSupplier;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * An MCP meta supplier that adds the W3C {@code traceparent} field to the {@code _meta}
 * of MCP requests when a span is active, enabling trace context propagation to MCP servers.
 */
public class TracingMcpMetaSupplier implements McpMetaSupplier {

    @Override
    public Map<String, Object> apply(McpCallContext context) {
        Span span = Span.current();
        SpanContext spanContext = span.getSpanContext();
        if (spanContext.isValid()) {
            String traceparent = "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-"
                    + spanContext.getTraceFlags().asHex();
            return Map.of("traceparent", traceparent);
        }
        return Collections.emptyMap();
    }
}