package io.quarkiverse.langchain4j.runtime.conversation;

/**
 * CDI event fired when a conversation begins via {@link ConversationContext#begin(String)}.
 */
public record ConversationStarted(String conversationId) {
}
