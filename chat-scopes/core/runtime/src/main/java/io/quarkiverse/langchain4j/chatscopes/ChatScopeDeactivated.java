package io.quarkiverse.langchain4j.chatscopes;

/**
 * CDI Event fired when a {@link ChatScope} is deactivated.
 */
public class ChatScopeDeactivated extends AbstractChatScopeCDIEvent {
    public ChatScopeDeactivated(ChatScope scope) {
        super(scope);
    }
}