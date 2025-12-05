package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Instance;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

class SpanToolWrapper implements Function<ToolExecutor, ToolExecutor> {

    private final Instance<Tracer> tracerInstance;

    SpanToolWrapper(Instance<Tracer> tracerInstance) {
        this.tracerInstance = tracerInstance;
    }

    @Override
    public ToolExecutor apply(ToolExecutor toolExecutor) {
        return new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
                Span span = tracerInstance.get().spanBuilder("langchain4j.mcp-tools." + toolExecutionRequest.name())
                        .startSpan();
                try (Scope scope = span.makeCurrent()) {
                    return toolExecutor.execute(toolExecutionRequest, memoryId);
                } catch (Throwable t) {
                    span.recordException(t);
                    throw t;
                } finally {
                    span.end();
                }
            }
        };
    }
}
