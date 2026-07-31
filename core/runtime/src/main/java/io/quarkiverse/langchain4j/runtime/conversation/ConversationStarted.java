package io.quarkiverse.langchain4j.runtime.conversation;

/**
 * CDI event fired when a conversation begins via {@link ConversationContext#begin(String)}.
 */
public class ConversationStarted {

    private final String conversationId;

    public ConversationStarted(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationId() {
        return conversationId;
    }
}
