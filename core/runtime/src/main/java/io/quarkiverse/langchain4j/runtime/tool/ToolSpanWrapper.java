package io.quarkiverse.langchain4j.runtime.tool;

import java.util.function.BiFunction;

import jakarta.inject.Inject;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class ToolSpanWrapper implements QuarkusToolExecutor.Wrapper {

    private final Tracer tracer;

    @Inject
    public ToolSpanWrapper(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public String wrap(ToolExecutionRequest toolExecutionRequest, Object memoryId,
            BiFunction<ToolExecutionRequest, Object, String> fun) {
        Span span = tracer.spanBuilder("langchain4j.tools." + toolExecutionRequest.name()).startSpan();
        try (Scope scope = span.makeCurrent()) {
            // TODO Handle async method here.
            return fun.apply(toolExecutionRequest, memoryId);
        } catch (Throwable t) {
            span.recordException(t);
            throw t;
        } finally {
            span.end();
        }
    }
}
