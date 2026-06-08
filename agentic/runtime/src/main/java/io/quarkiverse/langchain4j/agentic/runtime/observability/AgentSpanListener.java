package io.quarkiverse.langchain4j.agentic.runtime.observability;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class AgentSpanListener implements AgentListener {

    private static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    private static final AttributeKey<String> AGENT_ID = AttributeKey.stringKey("gen_ai.agent.id");
    private static final AttributeKey<Long> INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    private final ConcurrentHashMap<String, SpanScope> activeSpans = new ConcurrentHashMap<>();

    @Inject
    Tracer tracer;

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        Span span = tracer.spanBuilder("langchain4j.agent." + agentRequest.agentName())
                .setAttribute(OPERATION_NAME, "agent_invocation")
                .setAttribute(AGENT_NAME, agentRequest.agentName())
                .setAttribute(AGENT_ID, agentRequest.agentId())
                .startSpan();
        Scope scope = span.makeCurrent();
        activeSpans.put(agentRequest.agentId(), new SpanScope(span, scope));
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        SpanScope spanScope = activeSpans.remove(agentResponse.agentId());
        if (spanScope == null) {
            return;
        }
        try {
            TokenUsage tokenUsage = null;
            ChatResponse chatResponse = agentResponse.chatResponse();
            if (chatResponse != null) {
                ChatResponseMetadata metadata = chatResponse.metadata();
                if (metadata != null) {
                    tokenUsage = metadata.tokenUsage();
                }
            }
            if (tokenUsage != null) {
                if (tokenUsage.inputTokenCount() != null) {
                    spanScope.span.setAttribute(INPUT_TOKENS, tokenUsage.inputTokenCount().longValue());
                }
                if (tokenUsage.outputTokenCount() != null) {
                    spanScope.span.setAttribute(OUTPUT_TOKENS, tokenUsage.outputTokenCount().longValue());
                }
            }
        } finally {
            spanScope.scope.close();
            spanScope.span.end();
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError invocationError) {
        SpanScope spanScope = activeSpans.remove(invocationError.agentId());
        if (spanScope == null) {
            return;
        }
        try {
            spanScope.span.recordException(invocationError.error());
            spanScope.span.setStatus(StatusCode.ERROR, invocationError.error().getMessage());
        } finally {
            spanScope.scope.close();
            spanScope.span.end();
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    private record SpanScope(Span span, Scope scope) {
    }
}
