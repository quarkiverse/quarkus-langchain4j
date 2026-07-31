package io.quarkiverse.langchain4j.runtime.conversation;

import jakarta.enterprise.event.Event;

import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * Manages the current conversation identity for observability purposes.
 * <p>
 * The conversation ID is stored in Vert.x duplicated context locals (via {@link ContextLocals}),
 * which are per-request and safe on event loop threads. When OpenTelemetry is present, the
 * conversation ID is automatically set as a {@code gen_ai.conversation.id} span attribute.
 * <p>
 * When using Chat Scopes, the conversation ID is set automatically via an internal bridge.
 * For other transports, call {@link #begin(String)} and {@link #end()} explicitly.
 */
public final class ConversationContext {

    /**
     * The OpenTelemetry attribute key for the conversation ID, following the
     * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">GenAI semantic conventions</a>.
     */
    public static final String OTEL_ATTRIBUTE = "gen_ai.conversation.id";

    static final String CONTEXT_KEY = "langchain4j.conversation.id";

    private ConversationContext() {
    }

    /**
     * Begins a conversation with the given ID. Sets the conversation ID in the current
     * context and fires a {@link ConversationStarted} CDI event.
     * <p>
     * If a conversation is already active, it is ended first (firing a {@link ConversationEnded}
     * event for the previous conversation) before starting the new one.
     *
     * @param conversationId the conversation identifier
     */
    public static void begin(String conversationId) {
        end();
        ContextLocals.put(CONTEXT_KEY, conversationId);
        fireEvent(new ConversationStarted(conversationId));
    }

    /**
     * Returns the current conversation ID, or {@code null} if no conversation is active.
     */
    public static String current() {
        return ContextLocals.get(CONTEXT_KEY);
    }

    /**
     * Returns {@code true} if a conversation is currently active.
     */
    public static boolean isActive() {
        return current() != null;
    }

    /**
     * Ends the current conversation. Clears the conversation ID from the current context
     * and fires a {@link ConversationEnded} CDI event. Does nothing if no conversation is active.
     */
    public static void end() {
        String id = current();
        if (id != null) {
            ContextLocals.remove(CONTEXT_KEY);
            fireEvent(new ConversationEnded(id));
        }
    }

    // Package-private: used by ConversationThreadContextProvider for raw set/clear without events
    static void set(String conversationId) {
        if (conversationId != null) {
            ContextLocals.put(CONTEXT_KEY, conversationId);
        } else {
            ContextLocals.remove(CONTEXT_KEY);
        }
    }

    static void clear() {
        ContextLocals.remove(CONTEXT_KEY);
    }

    @SuppressWarnings("unchecked")
    private static void fireEvent(Object event) {
        ArcContainer container = Arc.container();
        if (container != null) {
            container.instance(Event.class).get().select(event.getClass()).fire(event);
        }
    }
}
