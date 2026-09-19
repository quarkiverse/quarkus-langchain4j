package io.quarkiverse.langchain4j.runtime.conversation;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;

/**
 * Shared helper for setting the conversation ID on OTel spans.
 * Only used by span contributors that are registered when OTel is present.
 */
public final class ConversationSpanHelper {

    /**
     * The OpenTelemetry attribute key for the conversation ID, following the
     * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">GenAI semantic conventions</a>.
     */
    public static final String OTEL_ATTRIBUTE = "gen_ai.conversation.id";

    private ConversationSpanHelper() {
    }

    public static void setConversationId(Span span) {
        String id = ConversationContext.current();
        if (id == null) {
            id = Baggage.current().getEntryValue(OTEL_ATTRIBUTE);
        }
        if (id != null) {
            span.setAttribute(OTEL_ATTRIBUTE, id);
        }
    }
}
