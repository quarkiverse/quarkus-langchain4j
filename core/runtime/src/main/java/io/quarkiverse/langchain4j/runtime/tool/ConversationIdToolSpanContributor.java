package io.quarkiverse.langchain4j.runtime.tool;

import io.opentelemetry.api.trace.Span;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationSpanHelper;

/**
 * Sets the {@code gen_ai.conversation.id} attribute on tool execution spans when a conversation is active.
 * Falls back to reading from OTel Baggage for cross-service propagation.
 */
public class ConversationIdToolSpanContributor implements ToolSpanContributor {

    @Override
    public void onRequest(ToolExecutionRequestContext context, Span span) {
        ConversationSpanHelper.setConversationId(span);
    }
}
