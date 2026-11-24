package io.quarkiverse.langchain4j.mcp.runtime;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpMetaSupplier;
import io.quarkus.arc.Arc;

/**
 * Adds the metrics and tracing listeners (and the trace context propagating meta supplier) to an MCP client builder.
 * <p>
 * Used for the MCP clients configured via {@code quarkus.langchain4j.mcp.<name>.*}, and available to other extensions
 * that create MCP clients at runtime, so these clients are observable in the same way.
 * The Micrometer and OpenTelemetry specific classes are loaded reflectively, so this class can be used when
 * those optional dependencies are absent (as long as the corresponding flag is {@code false}).
 */
public final class McpClientObservability {

    private McpClientObservability() {
    }

    /**
     * @param addMetrics whether to add the Micrometer metrics listener; only pass {@code true} when Micrometer is present
     * @param addTracing whether to add the OpenTelemetry tracing listener and meta supplier; only pass {@code true}
     *        when OpenTelemetry is present and enabled
     */
    public static void addObservability(DefaultMcpClient.Builder builder, String key, boolean addMetrics, boolean addTracing) {
        if (addMetrics) {
            builder.addListener(createMetricsListener(key));
        }
        if (addTracing) {
            builder.addListener(createTracingListener());
            builder.metaSupplier(createTracingMetaSupplier());
        }
    }

    private static McpClientListener createMetricsListener(String key) {
        try {
            // avoid direct dependency on the MetricsMcpListener class, it would break native
            // compilation if the optional Micrometer dependency isn't present
            return (McpClientListener) Thread.currentThread()
                    .getContextClassLoader().loadClass("io.quarkiverse.langchain4j.mcp.runtime.MetricsMcpListener")
                    .getConstructor(String.class).newInstance(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static McpClientListener createTracingListener() {
        try {
            // avoid direct dependency on the TracingMcpClientListener class, it would break native
            // compilation if the optional OpenTelemetry dependency isn't present
            Object tracer = Arc.container().select(
                    Thread.currentThread().getContextClassLoader()
                            .loadClass("io.opentelemetry.api.trace.Tracer"))
                    .get();
            return (McpClientListener) Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass("io.quarkiverse.langchain4j.mcp.runtime.TracingMcpClientListener")
                    .getConstructor(Thread.currentThread().getContextClassLoader()
                            .loadClass("io.opentelemetry.api.trace.Tracer"))
                    .newInstance(tracer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static McpMetaSupplier createTracingMetaSupplier() {
        try {
            // avoid direct dependency on the TracingMcpMetaSupplier class, it would break native
            // compilation if the optional OpenTelemetry dependency isn't present
            return (McpMetaSupplier) Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass("io.quarkiverse.langchain4j.mcp.runtime.TracingMcpMetaSupplier")
                    .getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
