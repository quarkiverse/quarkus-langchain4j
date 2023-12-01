package io.quarkiverse.langchain4j.runtime.tool;

import java.util.function.BiFunction;

import jakarta.inject.Inject;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class ToolSpanWrapper implements QuarkusToolExecutor.Wrapper {

    private static final String INSTRUMENTATION_NAME = "io.quarkus.opentelemetry";

    private final Instrumenter<ToolExecutionRequest, Void> instrumenter;

    @Inject
    public ToolSpanWrapper(OpenTelemetry openTelemetry) {
        InstrumenterBuilder<ToolExecutionRequest, Void> builder = Instrumenter.builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                InputSpanNameExtractor.INSTANCE);

        // TODO: there is probably more information here we need to set
        this.instrumenter = builder
                .buildInstrumenter(new SpanKindExtractor<>() {
                    @Override
                    public SpanKind extract(ToolExecutionRequest toolExecutionRequest) {
                        return SpanKind.INTERNAL;
                    }
                });
    }

    @Override
    public String wrap(ToolExecutionRequest toolExecutionRequest, Object memoryId,
            BiFunction<ToolExecutionRequest, Object, String> fun) {
        Context parentContext = Context.current();
        Context spanContext = null;
        Scope scope = null;
        boolean shouldStart = instrumenter.shouldStart(parentContext, toolExecutionRequest);
        if (shouldStart) {
            spanContext = instrumenter.start(parentContext, toolExecutionRequest);
            scope = spanContext.makeCurrent();
        }

        try {
            String result = fun.apply(toolExecutionRequest, memoryId);

            if (shouldStart) {
                instrumenter.end(spanContext, toolExecutionRequest, null, null);
            }

            return result;
        } catch (Throwable t) {
            if (shouldStart) {
                instrumenter.end(spanContext, toolExecutionRequest, null, t);
            }
            throw t;
        } finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    private static class InputSpanNameExtractor implements SpanNameExtractor<ToolExecutionRequest> {

        private static final InputSpanNameExtractor INSTANCE = new InputSpanNameExtractor();

        @Override
        public String extract(ToolExecutionRequest toolExecutionRequest) {
            return "langchain4j.tools." + toolExecutionRequest.name();
        }
    }
}
