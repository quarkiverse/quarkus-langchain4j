package io.quarkiverse.langchain4j.runtime.listeners;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkiverse.langchain4j.cost.Cost;
import io.quarkiverse.langchain4j.cost.CostEstimatorService;
import io.quarkus.arc.All;

/**
 * Creates spans that follow the
 * <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/gen-ai-spans.md">Semantic Conventions
 * for GenAI operations</a>.
 * <p>
 * Each exchange produces two spans: an {@code open} span started and ended in the request callback, and a
 * {@code close} span started and ended in the response or error callback. A streaming model delivers its
 * response on another thread, possibly long after the request, so no span can stay open across the stream:
 * a {@link Scope} is autocloseable and can only be closed on the thread that opened it. Relevant happenings
 * of an in-flight exchange (an error, contributor failures) are recorded as events on the closing span.
 */
public class SpanChatModelListener implements ChatModelListener {

    private static final Logger log = Logger.getLogger(SpanChatModelListener.class);

    private static final String OTEL_SPAN_KEY_NAME = "OTelSpan";
    public static final String OTEL_PARENT_SPAN_KEY_NAME = "OTelParentSpan";

    private final Tracer tracer;
    private final CostEstimatorService costEstimatorService;
    private final List<ChatModelSpanContributor> chatModelSpanContributors;

    @Inject
    public SpanChatModelListener(Tracer tracer, CostEstimatorService costEstimatorService,
            @All List<ChatModelSpanContributor> chatModelSpanContributors) {
        this.tracer = tracer;
        this.costEstimatorService = costEstimatorService;
        this.chatModelSpanContributors = chatModelSpanContributors;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        ChatRequest request = requestContext.chatRequest();
        var attributes = requestContext.attributes();
        var parentSpan = Span.current();

        Span span = tracer.spanBuilder("chat " + request.parameters().modelName() + " open")
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.provider.name", requestContext.modelProvider().toString().toLowerCase())
                .setAttribute("gen_ai.request.model", request.parameters().modelName())
                .setAttribute("gen_ai.request.temperature",
                        request.parameters().temperature() != null ? request.parameters().temperature() : 0D)
                .setAttribute("gen_ai.request.top_p", request.parameters().topP() != null ? request.parameters().topP() : 0D)
                .startSpan();

        attributes.put(OTEL_PARENT_SPAN_KEY_NAME, parentSpan.getSpanContext().isValid() ? parentSpan : span);
        attributes.put(OTEL_SPAN_KEY_NAME, span);

        // the span is started and ended within this callback, nothing is left open across the stream
        try (Scope scope = span.makeCurrent()) {
            notifyContributorsOnRequest(requestContext, span);
        } finally {
            span.end();
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Span span = startClosingSpan(responseContext.chatRequest(), responseContext.modelProvider(),
                responseContext.attributes());
        try (Scope scope = span.makeCurrent()) {
            ChatResponse response = responseContext.chatResponse();
            span.setAttribute("gen_ai.response.id", response.metadata().id());
            if (response.metadata().modelName() != null) {
                span.setAttribute("gen_ai.response.model", response.metadata().modelName());
            }
            if (response.finishReason() != null) {
                span.setAttribute("gen_ai.response.finish_reasons", response.finishReason().toString());
            }
            TokenUsage tokenUsage = response.tokenUsage();
            if (tokenUsage != null) {
                span.setAttribute("gen_ai.usage.output_tokens", tokenUsage.outputTokenCount())
                        .setAttribute("gen_ai.usage.input_tokens", tokenUsage.inputTokenCount());

                Cost costEstimate = costEstimatorService.estimate(responseContext);
                if (costEstimate != null) {
                    span.setAttribute("gen_ai.client.estimated_cost", costEstimate.toString());
                }
            }
            notifyContributorsOnResponse(responseContext, span);
        } finally {
            span.end();
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Span span = startClosingSpan(errorContext.chatRequest(), errorContext.modelProvider(), errorContext.attributes());
        try (Scope scope = span.makeCurrent()) {
            // the error is a happening of the in-flight exchange, recorded as an event on the closing span
            span.recordException(errorContext.error());
            notifyContributorsOnError(errorContext, span);
        } finally {
            span.end();
        }
    }

    private Span startClosingSpan(ChatRequest request, ModelProvider modelProvider, Map<Object, Object> attributes) {
        var builder = tracer.spanBuilder("chat " + request.parameters().modelName() + " close")
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.provider.name", modelProvider.toString().toLowerCase())
                .setAttribute("gen_ai.request.model", request.parameters().modelName());
        Span openingSpan = (Span) attributes.get(OTEL_SPAN_KEY_NAME);
        if (openingSpan != null) {
            builder.setParent(Context.root().with(openingSpan));
        } else {
            // should never happen
            log.warn("No opening span found in attributes");
        }
        return builder.startSpan();
    }

    private void notifyContributorsOnRequest(ChatModelRequestContext requestContext, Span span) {
        for (ChatModelSpanContributor contributor : chatModelSpanContributors) {
            try {
                contributor.onRequest(requestContext, span);
            } catch (Exception ex) {
                recordLogAndSwallow(span, ex);
            }
        }
    }

    private void notifyContributorsOnResponse(ChatModelResponseContext responseContext, Span span) {
        for (ChatModelSpanContributor contributor : chatModelSpanContributors) {
            try {
                contributor.onResponse(responseContext, span);
            } catch (Exception ex) {
                recordLogAndSwallow(span, ex);
            }
        }
    }

    private void notifyContributorsOnError(ChatModelErrorContext errorContext, Span span) {
        for (ChatModelSpanContributor contributor : chatModelSpanContributors) {
            try {
                contributor.onError(errorContext, span);
            } catch (Exception ex) {
                recordLogAndSwallow(span, ex);
            }
        }
    }

    private void recordLogAndSwallow(Span span, Exception ex) {
        span.recordException(ex);
        log.warn("failure on contributor", ex);
    }
}
