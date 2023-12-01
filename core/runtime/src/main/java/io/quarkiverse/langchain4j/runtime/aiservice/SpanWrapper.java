package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class SpanWrapper implements AiServiceMethodImplementationSupport.Wrapper {

    private static final String INSTRUMENTATION_NAME = "io.quarkus.opentelemetry";

    private final Instrumenter<AiServiceMethodImplementationSupport.Input, Void> instrumenter;

    @Inject
    public SpanWrapper(OpenTelemetry openTelemetry) {
        InstrumenterBuilder<AiServiceMethodImplementationSupport.Input, Void> builder = Instrumenter.builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                InputSpanNameExtractor.INSTANCE);

        // TODO: there is probably more information here we need to set
        this.instrumenter = builder
                .buildInstrumenter(new SpanKindExtractor<AiServiceMethodImplementationSupport.Input>() {
                    @Override
                    public SpanKind extract(AiServiceMethodImplementationSupport.Input input) {
                        return SpanKind.INTERNAL;
                    }
                });
    }

    @Override
    public Object wrap(AiServiceMethodImplementationSupport.Input input,
            Function<AiServiceMethodImplementationSupport.Input, Object> fun) {

        Context parentContext = Context.current();
        Context spanContext = null;
        Scope scope = null;
        boolean shouldStart = instrumenter.shouldStart(parentContext, input);
        if (shouldStart) {
            spanContext = instrumenter.start(parentContext, input);
            scope = spanContext.makeCurrent();
        }

        try {
            Object result = fun.apply(input);

            if (shouldStart) {
                instrumenter.end(spanContext, input, null, null);
            }

            return result;
        } catch (Throwable t) {
            if (shouldStart) {
                instrumenter.end(spanContext, input, null, t);
            }
            throw t;
        } finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    private static class InputSpanNameExtractor implements SpanNameExtractor<AiServiceMethodImplementationSupport.Input> {

        private static final InputSpanNameExtractor INSTANCE = new InputSpanNameExtractor();

        @Override
        public String extract(AiServiceMethodImplementationSupport.Input input) {
            Optional<AiServiceMethodCreateInfo.SpanInfo> spanInfoOpt = input.createInfo.getSpanInfo();
            if (spanInfoOpt.isPresent()) {
                return spanInfoOpt.get().getName();
            }
            return null;
        }
    }
}
