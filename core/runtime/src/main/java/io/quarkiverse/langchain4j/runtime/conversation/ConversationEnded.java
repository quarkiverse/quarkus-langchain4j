package io.quarkiverse.langchain4j.runtime.conversation;

/**
 * CDI event fired when a conversation ends via {@link ConversationContext#end()}.
 */
public class ConversationEnded {

    private final String conversationId;

    public ConversationEnded(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationId() {
        return conversationId;
    }
}
