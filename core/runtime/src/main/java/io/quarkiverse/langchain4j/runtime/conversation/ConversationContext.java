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

    static final String CONTEXT_KEY = "langchain4j.conversation.id";

    private static final ThreadLocal<String> FALLBACK = new ThreadLocal<>();

    private ConversationContext() {
    }

    /**
     * Begins a conversation with the given ID. Sets the conversation ID in the current
     * context and fires a {@link ConversationStarted} CDI event.
     * <p>
     * If a conversation is already active, it is ended first (firing a {@link ConversationEnded}
     * event for the previous conversation) before starting the new one.
     *
     * @param conversationId the conversation identifier (must not be {@code null})
     * @throws IllegalArgumentException if conversationId is null
     */
    public static void begin(String conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId must not be null");
        }
        end();
        if (ContextLocals.duplicatedContextActive()) {
            ContextLocals.put(CONTEXT_KEY, conversationId);
        } else {
            FALLBACK.set(conversationId);
        }
        fireEvent(new ConversationStarted(conversationId));
    }

    /**
     * Returns the current conversation ID, or {@code null} if no conversation is active.
     */
    public static String current() {
        if (ContextLocals.duplicatedContextActive()) {
            return ContextLocals.get(CONTEXT_KEY);
        }
        return FALLBACK.get();
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
            if (ContextLocals.duplicatedContextActive()) {
                ContextLocals.remove(CONTEXT_KEY);
            } else {
                FALLBACK.remove();
            }
            fireEvent(new ConversationEnded(id));
        }
    }

    // Package-private: used by ConversationThreadContextProvider for raw set/clear without events
    static void set(String conversationId) {
        if (conversationId != null) {
            if (ContextLocals.duplicatedContextActive()) {
                ContextLocals.put(CONTEXT_KEY, conversationId);
            } else {
                FALLBACK.set(conversationId);
            }
        } else {
            clear();
        }
    }

    static void clear() {
        ContextLocals.remove(CONTEXT_KEY);
        FALLBACK.remove();
    }

    @SuppressWarnings("unchecked")
    private static void fireEvent(Object event) {
        ArcContainer container = Arc.container();
        if (container != null) {
            container.instance(Event.class).get().select(event.getClass()).fire(event);
        }
    }
}
