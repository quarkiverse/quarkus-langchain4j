package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class SpanWrapper implements AiServiceMethodImplementationSupport.Wrapper {

    private final Tracer tracer;

    @Inject
    public SpanWrapper(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object wrap(AiServiceMethodImplementationSupport.Input input,
            Function<AiServiceMethodImplementationSupport.Input, Object> fun) {

        Optional<AiServiceMethodCreateInfo.SpanInfo> spanInfoOpt = input.createInfo.getSpanInfo();
        if (spanInfoOpt.isEmpty()) {
            return fun.apply(input);
        }

        Span span = tracer.spanBuilder(spanInfoOpt.get().name()).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return fun.apply(input);
        } catch (Throwable t) {
            span.recordException(t);
            throw t;
        } finally {
            span.end();
        }
    }

}
