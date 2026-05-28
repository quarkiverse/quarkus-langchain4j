package io.quarkiverse.langchain4j.chatscopes;

public abstract class AbstractChatScopeCDIEvent implements ChatScopeCDIEvent {
    protected final ChatScope scope;

    protected AbstractChatScopeCDIEvent(ChatScope scope) {
        this.scope = scope;
    }

    @Override
    public ChatScope scope() {
        return scope;
    }
}