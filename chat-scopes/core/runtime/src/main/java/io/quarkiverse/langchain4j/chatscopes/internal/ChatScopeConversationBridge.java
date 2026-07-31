package io.quarkiverse.langchain4j.chatscopes.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkiverse.langchain4j.chatscopes.ChatScopeEnded;
import io.quarkiverse.langchain4j.chatscopes.ChatScopeStarted;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;

/**
 * Bridges ChatScope lifecycle events to {@link ConversationContext}, so that the conversation ID
 * is automatically set on OTel spans when using Chat Scopes.
 * <p>
 * Only reacts to top-level scopes (where {@code parent() == null}). Push/pop transitions within
 * a conversation do not affect the conversation identity — the ID remains that of the top scope
 * throughout the entire conversation lifecycle.
 */
@ApplicationScoped
public class ChatScopeConversationBridge {

    void onStarted(@Observes ChatScopeStarted event) {
        if (event.scope().parent() == null) {
            ConversationContext.begin(event.scope().getId());
        }
    }

    void onEnded(@Observes ChatScopeEnded event) {
        if (event.scope().parent() == null) {
            ConversationContext.end();
        }
    }
}
