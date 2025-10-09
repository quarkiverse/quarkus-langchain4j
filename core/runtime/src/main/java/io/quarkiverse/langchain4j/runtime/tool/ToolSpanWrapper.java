package io.quarkiverse.langchain4j.runtime.tool;

import java.util.function.BiFunction;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class ToolSpanWrapper implements QuarkusToolExecutor.Wrapper {

    private final Tracer tracer;
    private final boolean includeArguments;
    private final boolean includeResult;

    @Inject
    public ToolSpanWrapper(Tracer tracer,
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-tool-arguments") boolean includeArguments,
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-tool-result") boolean includeResult) {

        this.tracer = tracer;
        this.includeArguments = includeArguments;
        this.includeResult = includeResult;
    }

    @Override
    public ToolExecutionResult wrap(ToolExecutionRequest toolExecutionRequest, InvocationContext invocationContext,
            BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> fun) {

        // from https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/gen-ai-spans.md#execute-tool-span
        Span span = tracer.spanBuilder("langchain4j.tools." + toolExecutionRequest.name())
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("gen_ai.operation.name", "execute_tool")
                .setAttribute("gen_ai.tool.call.id", toolExecutionRequest.id())
                .setAttribute("gen_ai.tool.name", toolExecutionRequest.name())
                .setAttribute("gen_ai.tool.type", "function")
                .startSpan();
        if (includeArguments) {
            span.setAttribute("gen_ai.tool.call.arguments", toolExecutionRequest.arguments());
        }
        try (Scope ignored = span.makeCurrent()) {
            // TODO Handle async method here.
            var result = fun.apply(toolExecutionRequest, invocationContext);
            if (includeResult && result != null) {
                span.setAttribute("gen_ai.tool.call.result", result.resultText());
            }
            return result;
        } catch (Throwable t) {
            span.recordException(t);
            throw t;
        } finally {
            span.end();
        }
    }
}
