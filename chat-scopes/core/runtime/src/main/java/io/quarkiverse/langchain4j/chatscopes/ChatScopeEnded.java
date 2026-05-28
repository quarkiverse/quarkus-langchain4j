package io.quarkiverse.langchain4j.chatscopes;

/**
 * CDI event fired when a {@link ChatScope} is ended.
 */
public class ChatScopeEnded extends AbstractChatScopeCDIEvent {
    public ChatScopeEnded(ChatScope scope) {
        super(scope);
    }
}