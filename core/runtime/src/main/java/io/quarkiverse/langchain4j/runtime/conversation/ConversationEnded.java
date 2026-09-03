package io.quarkiverse.langchain4j.runtime.conversation;

/**
 * CDI event fired when a conversation ends via {@link ConversationContext#end()}.
 */
public record ConversationEnded(String conversationId) {
}
