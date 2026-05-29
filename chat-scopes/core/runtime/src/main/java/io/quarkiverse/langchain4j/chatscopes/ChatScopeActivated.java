package io.quarkiverse.langchain4j.chatscopes;

/**
 * CDI Event fired when a {@link ChatScope} is activated.
 */
public class ChatScopeActivated extends AbstractChatScopeCDIEvent {
    public ChatScopeActivated(ChatScope scope) {
        super(scope);
    }
}