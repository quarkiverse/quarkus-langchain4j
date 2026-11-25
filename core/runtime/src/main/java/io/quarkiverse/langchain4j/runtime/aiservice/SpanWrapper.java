package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.service.TokenStream;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkiverse.langchain4j.runtime.types.TypeUtil;
import io.quarkus.arc.All;
import io.smallrye.mutiny.Multi;

public class SpanWrapper implements AiServiceMethodImplementationSupport.Wrapper {

    private final Tracer tracer;
    private final boolean includeArguments;
    private final boolean includeResult;
    private final List<AiServiceSpanContributor> contributors;

    @Inject
    public SpanWrapper(Tracer tracer,
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-ai-service-arguments") boolean includeArguments,
            @ConfigProperty(name = "quarkus.langchain4j.tracing.include-ai-service-result") boolean includeResult,
            @All List<AiServiceSpanContributor> contributors) {
        this.tracer = tracer;
        this.includeArguments = includeArguments;
        this.includeResult = includeResult;
        this.contributors = contributors;
    }

    @Override
    public Object wrap(AiServiceMethodImplementationSupport.Input input,
            Function<AiServiceMethodImplementationSupport.Input, Object> fun) {

        Optional<AiServiceMethodCreateInfo.SpanInfo> spanInfoOpt = input.createInfo.getSpanInfo();

        return spanInfoOpt.isEmpty() ? fun.apply(input) : wrapInSpan(spanInfoOpt.get(), input, fun);
    }

    private Object wrapInSpan(AiServiceMethodCreateInfo.SpanInfo spanInfo, AiServiceMethodImplementationSupport.Input input,
            Function<AiServiceMethodImplementationSupport.Input, Object> fun) {

        Context parent = Context.current();
        AiServiceSpan span = startSpan(spanInfo, input, parent, Optional.empty());

        try (Scope ignored = span.makeCurrent()) {
            return endWhenDone(fun.apply(input), spanInfo, input, parent, span);
        } catch (Throwable t) {
            span.fail(t);
            throw t;
        }
    }

    /**
     * @param parent the context the AI service method was called in
     * @param link the span of the first subscription, when this span is for a later subscription to the same stream
     */
    private AiServiceSpan startSpan(AiServiceMethodCreateInfo.SpanInfo spanInfo,
            AiServiceMethodImplementationSupport.Input input, Context parent, Optional<SpanContext> link) {
        SpanBuilder builder = tracer.spanBuilder(spanInfo.name())
                .setParent(parent)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AiServiceSpanAttributes.GEN_AI_OPERATION_NAME, AiServiceSpanAttributes.INVOKE_AI_SERVICE);
        if (link.isPresent()) {
            builder.addLink(link.get());
        }
        AiServiceSpan span = new AiServiceSpan(builder.startSpan(), spanInfo, input.createInfo, input.methodArgs,
                includeArguments, includeResult, contributors);
        try (Scope ignored = span.makeCurrent()) {
            span.start(input.methodArgs);
        }
        return span;
    }

    /**
     * A stream is returned before the model is called, so its span ends when the stream terminates. Any other result is
     * complete once the method returns.
     * <p>
     * The span starts when the method is called rather than when the stream is subscribed to: a streaming method reads
     * the chat memory, runs the input guardrails and the RAG retrieval before it returns the stream, and only the model
     * call waits for the subscription. Starting on subscription would leave that work and its child spans outside the
     * span, or would move it, and its failures, to subscription time. As a consequence, the span of a stream that is
     * never subscribed to never ends, and is never exported.
     * <p>
     * Each subscription to a {@code Multi} calls the model again, so each one gets its own span: the first subscription
     * takes the span started here, and every later one, a retry for example, starts a new span with the same parent and
     * a link to the first one.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object endWhenDone(Object result, AiServiceMethodCreateInfo.SpanInfo spanInfo,
            AiServiceMethodImplementationSupport.Input input, Context parent, AiServiceSpan span) {
        AiServiceMethodCreateInfo createInfo = input.createInfo;
        Object wrapped;
        if (TypeUtil.isMultiOrFlowPublisher(createInfo.getReturnType()) && (result instanceof Multi multi)) {
            Optional<SpanContext> firstSpan = Optional.of(span.spanContext());
            wrapped = new SpanEndingMulti(multi, span, new Supplier<AiServiceSpan>() {
                @Override
                public AiServiceSpan get() {
                    return startSpan(spanInfo, input, parent, firstSpan);
                }
            });
        } else if (TypeUtil.isTokenStream(createInfo.getReturnType()) && (result instanceof TokenStream tokenStream)) {
            wrapped = new SpanEndingTokenStream(tokenStream, span);
        } else {
            span.complete(result);
            wrapped = result;
        }
        return wrapped;
    }
}
