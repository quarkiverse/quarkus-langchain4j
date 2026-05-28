package io.quarkiverse.langchain4j.chatscopes;

/**
 * Various classes implement this to send chat scope lifecycle events like
 * chat scope starting, ending, etc.
 */
public interface ChatScopeCDIEvent {
    /**
     * Chat scope id.
     *
     * @return
     */
    ChatScope scope();
}
