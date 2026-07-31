package io.quarkiverse.langchain4j.runtime.listeners;

import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;

/**
 * Sets the {@code gen_ai.conversation.id} attribute on LLM call spans when a conversation is active.
 * Falls back to reading from OTel Baggage for cross-service propagation.
 */
public class ConversationIdSpanContributor implements ChatModelSpanContributor {

    @Override
    public void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {
        String id = ConversationContext.current();
        if (id == null) {
            id = Baggage.current().getEntryValue(ConversationContext.OTEL_ATTRIBUTE);
        }
        if (id != null) {
            currentSpan.setAttribute(ConversationContext.OTEL_ATTRIBUTE, id);
        }
    }
}
