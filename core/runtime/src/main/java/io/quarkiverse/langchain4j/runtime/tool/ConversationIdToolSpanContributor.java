package io.quarkiverse.langchain4j.runtime.tool;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;

/**
 * Sets the {@code gen_ai.conversation.id} attribute on tool execution spans when a conversation is active.
 * Falls back to reading from OTel Baggage for cross-service propagation.
 */
public class ConversationIdToolSpanContributor implements ToolSpanContributor {

    @Override
    public void onRequest(ToolExecutionRequestContext context, Span span) {
        String id = ConversationContext.current();
        if (id == null) {
            id = Baggage.current().getEntryValue(ConversationContext.OTEL_ATTRIBUTE);
        }
        if (id != null) {
            span.setAttribute(ConversationContext.OTEL_ATTRIBUTE, id);
        }
    }
}
