package io.quarkiverse.langchain4j.chatscopes;

/**
 * CDI event fired when a {@link ChatScope} is started.
 */
public class ChatScopeStarted extends AbstractChatScopeCDIEvent {
    public ChatScopeStarted(ChatScope scope) {
        super(scope);
    }
}