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
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsageResolver;
import io.quarkiverse.langchain4j.cost.Cost;
import io.quarkiverse.langchain4j.cost.CostEstimatorService;
import io.quarkiverse.langchain4j.cost.ReasoningTokenUsageExtractor;
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
    public static final String OTEL_PARENT_SPAN_KEY_NAME = "OTelParentSpan";

    private final Tracer tracer;
    private final CostEstimatorService costEstimatorService;
    private final List<ChatModelSpanContributor> chatModelSpanContributors;

    private final CacheTokenUsageResolver cacheTokenUsageResolver;
    private final List<ReasoningTokenUsageExtractor> reasoningTokenUsageExtractors;

    @Inject
    public SpanChatModelListener(Tracer tracer, CostEstimatorService costEstimatorService,
            @All List<ChatModelSpanContributor> chatModelSpanContributors,
            CacheTokenUsageResolver cacheTokenUsageResolver,
            @All List<ReasoningTokenUsageExtractor> reasoningTokenUsageExtractors) {
        this.tracer = tracer;
        this.costEstimatorService = costEstimatorService;
        this.chatModelSpanContributors = chatModelSpanContributors;
        this.cacheTokenUsageResolver = cacheTokenUsageResolver;
        this.reasoningTokenUsageExtractors = reasoningTokenUsageExtractors;
    }

    /**
     * @deprecated Use the constructor that also takes the cache and reasoning token usage sources; this one records
     *             only the input and output token counts.
     */
    @Deprecated
    public SpanChatModelListener(Tracer tracer, CostEstimatorService costEstimatorService,
            List<ChatModelSpanContributor> chatModelSpanContributors) {
        this(tracer, costEstimatorService, chatModelSpanContributors, new CacheTokenUsageResolver(List.of()), List.of());
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        ChatRequest request = requestContext.chatRequest();
        var attributes = requestContext.attributes();
        var parentSpan = Span.current();

        Span span = tracer.spanBuilder("completion " + request.parameters().modelName())
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.provider.name", requestContext.modelProvider().toString().toLowerCase())
                .setAttribute("gen_ai.request.model", request.parameters().modelName())
                .setAttribute("gen_ai.request.temperature",
                        request.parameters().temperature() != null ? request.parameters().temperature() : 0D)
                .setAttribute("gen_ai.request.top_p", request.parameters().topP() != null ? request.parameters().topP() : 0D)
                .startSpan();
        Scope scope = span.makeCurrent();

        attributes.put(OTEL_PARENT_SPAN_KEY_NAME, parentSpan.getSpanContext().isValid() ? parentSpan : span);
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
                setTokenUsageAttributes(span, tokenUsage);

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

    /**
     * Sets the {@code gen_ai.usage.*} attributes. As the semantic conventions require, the output token count includes
     * the reasoning tokens. The input token count is passed on as the provider reports it.
     */
    private void setTokenUsageAttributes(Span span, TokenUsage tokenUsage) {
        Integer outputTokens = tokenUsage.outputTokenCount();
        ReasoningTokenUsageExtractor reasoningExtractor = findReasoningTokenUsageExtractor(tokenUsage);
        Integer reasoningTokens = reasoningExtractor != null ? reasoningExtractor.reasoningOutputTokens(tokenUsage) : null;
        if (reasoningTokens != null && reasoningTokens > 0) {
            span.setAttribute("gen_ai.usage.reasoning.output_tokens", reasoningTokens);
            if (outputTokens != null && !reasoningExtractor.outputTokenCountIncludesReasoning()) {
                outputTokens = outputTokens + reasoningTokens;
            }
        }
        if (outputTokens != null) {
            span.setAttribute("gen_ai.usage.output_tokens", outputTokens);
        }
        if (tokenUsage.inputTokenCount() != null) {
            span.setAttribute("gen_ai.usage.input_tokens", tokenUsage.inputTokenCount());
        }
        CacheTokenUsage cacheTokenUsage = cacheTokenUsageResolver.resolve(tokenUsage);
        Integer cacheReadInputTokens = cacheTokenUsage.cacheReadInputTokens();
        if (cacheReadInputTokens != null && cacheReadInputTokens > 0) {
            span.setAttribute("gen_ai.usage.cache_read.input_tokens", cacheReadInputTokens);
        }
        Integer cacheCreationInputTokens = cacheTokenUsage.cacheCreationInputTokens();
        if (cacheCreationInputTokens != null && cacheCreationInputTokens > 0) {
            span.setAttribute("gen_ai.usage.cache_creation.input_tokens", cacheCreationInputTokens);
        }
    }

    private ReasoningTokenUsageExtractor findReasoningTokenUsageExtractor(TokenUsage tokenUsage) {
        for (ReasoningTokenUsageExtractor extractor : reasoningTokenUsageExtractors) {
            if (extractor.supports(tokenUsage)) {
                return extractor;
            }
        }
        return null;
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
