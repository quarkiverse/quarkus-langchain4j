package io.quarkiverse.langchain4j.runtime.listeners;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.quarkiverse.langchain4j.cost.Cost;
import io.quarkiverse.langchain4j.cost.CostEstimatorService;
import io.quarkus.arc.All;

/**
 * Creates a span that follows the
 * <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/gen-ai-spans.md">Semantic Conventions
 * for GenAI operations</a>
 */
public class SpanChatModelListener implements ChatModelListener {

    private static final Logger log = Logger.getLogger(SpanChatModelListener.class);

    private static final String OTEL_SCOPE_KEY_NAME = "OTelScope";
    private static final String OTEL_SPAN_KEY_NAME = "OTelSpan";

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
        Span span = tracer.spanBuilder("completion " + request.parameters().modelName())
                .setAttribute("gen_ai.request.model", request.parameters().modelName())
                .setAttribute("gen_ai.request.temperature", request.parameters().temperature())
                .setAttribute("gen_ai.request.top_p", request.parameters().topP())
                .startSpan();
        Scope scope = span.makeCurrent();

        var attributes = requestContext.attributes();
        attributes.put(OTEL_SCOPE_KEY_NAME, scope);
        attributes.put(OTEL_SPAN_KEY_NAME, span);
        notifyContributorsOnRequest(requestContext, span);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        var attributes = responseContext.attributes();
        Span span = (Span) attributes.get(OTEL_SPAN_KEY_NAME);
        if (span != null) {
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
                span.setAttribute("gen_ai.usage.completion_tokens", tokenUsage.outputTokenCount())
                        .setAttribute("gen_ai.usage.prompt_tokens", tokenUsage.inputTokenCount());

                Cost costEstimate = costEstimatorService.estimate(responseContext);
                if (costEstimate != null) {
                    span.setAttribute("gen_ai.client.estimated_cost", costEstimate.toString());
                }
            }
            notifyContributorsOnResponse(responseContext, span);
            span.end();
        } else {
            // should never happen
            log.warn("No Span found in response");
        }
        safeCloseScope(attributes);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        var attributes = errorContext.attributes();
        Span span = (Span) attributes.get(OTEL_SPAN_KEY_NAME);
        if (span != null) {
            span.recordException(errorContext.error());
            notifyContributorsOnError(errorContext, span);
            span.end();
        } else {
            // should never happen
            log.warn("No Span found in response");
        }
        safeCloseScope(errorContext.attributes());
    }

    private void safeCloseScope(Map<Object, Object> attributes) {
        Scope scope = (Scope) attributes.get(OTEL_SCOPE_KEY_NAME);
        if (scope == null) {
            // should never happen
            log.warn("No Scope found in response");
        } else {
            try {
                scope.close();
            } catch (Exception e) {
                log.warn("Error closing scope", e);
            }
        }
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
