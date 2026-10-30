package io.quarkiverse.langchain4j.runtime.tool;

import java.util.List;
import java.util.function.BiFunction;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.quarkus.arc.All;

public class ToolSpanWrapper implements QuarkusToolExecutor.Wrapper {
    private static final Logger LOG = Logger.getLogger(ToolSpanWrapper.class);
    private static final String OTEL_SCOPE_KEY_NAME = "OTelScope";
    private static final String OTEL_SPAN_KEY_NAME = "OTelSpan";
    public static final String OTEL_PARENT_SPAN_KEY_NAME = "OTelParentSpan";

    private final Tracer tracer;
    private final boolean includeArguments;
    private final boolean includeResult;
    private final List<ToolSpanContributor> toolSpanContributors;

    @Inject
    public ToolSpanWrapper(Tracer tracer,
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-tool-arguments") boolean includeArguments,
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-tool-result") boolean includeResult,
            @All List<ToolSpanContributor> toolSpanContributors) {

        this.tracer = tracer;
        this.includeArguments = includeArguments;
        this.includeResult = includeResult;
        this.toolSpanContributors = toolSpanContributors;
    }

    @Override
    public ToolExecutionResult wrap(ToolExecutionRequest toolExecutionRequest, InvocationContext invocationContext,
            BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> fun, QuarkusToolExecutor executor) {

        var parentSpan = Span.current();

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

        var requestContext = ToolExecutionRequestContext.builder()
                .request(toolExecutionRequest)
                .invocationContext(invocationContext)
                .build();

        try (Scope scope = span.makeCurrent()) {
            requestContext = requestContext.toBuilder()
                    .attribute(OTEL_PARENT_SPAN_KEY_NAME, parentSpan.getSpanContext().isValid() ? parentSpan : span)
                    .attribute(OTEL_SCOPE_KEY_NAME, scope)
                    .attribute(OTEL_SPAN_KEY_NAME, span)
                    .build();
            notifyContributorsOnRequest(requestContext, span);
            // TODO Handle async method here.
            var result = fun.apply(toolExecutionRequest, invocationContext);
            if (includeResult && result != null) {
                span.setAttribute("gen_ai.tool.call.result", result.resultText());
            }

            var responseContext = ToolExecutionResponseContext.builder()
                    .requestContext(requestContext)
                    .result(result)
                    .build();

            notifyContributorsOnResponse(responseContext, span);

            return result;
        } catch (Throwable t) {
            span.recordException(t);

            var errorContext = ToolExecutionErrorContext.builder()
                    .requestContext(requestContext)
                    .error(t)
                    .build();

            notifyContributorsOnError(errorContext, span);

            throw t;
        } finally {
            span.end();
        }
    }

    private void notifyContributorsOnRequest(ToolExecutionRequestContext requestContext, Span span) {
        for (ToolSpanContributor contributor : this.toolSpanContributors) {
            try {
                contributor.onRequest(requestContext, span);
            } catch (Exception ex) {
                recordLogAndSwallow(span, ex);
            }
        }
    }

    private void notifyContributorsOnResponse(ToolExecutionResponseContext responseContext, Span span) {
        for (ToolSpanContributor contributor : this.toolSpanContributors) {
            try {
                contributor.onResponse(responseContext, span);
            } catch (Exception ex) {
                recordLogAndSwallow(span, ex);
            }
        }
    }

    private void notifyContributorsOnError(ToolExecutionErrorContext errorContext, Span span) {
        for (ToolSpanContributor contributor : this.toolSpanContributors) {
            try {
                contributor.onError(errorContext, span);
            } catch (Exception ex) {
                recordLogAndSwallow(span, ex);
            }
        }
    }

    private void recordLogAndSwallow(Span span, Exception ex) {
        span.recordException(ex);
        LOG.warn("failure on contributor", ex);
    }
}
