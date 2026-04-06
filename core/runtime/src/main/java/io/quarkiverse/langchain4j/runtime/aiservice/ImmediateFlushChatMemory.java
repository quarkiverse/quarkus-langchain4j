package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

/**
 * <pre>
 * A CommittableChatMemory that delegates directly to the real ChatMemory,
 * so every add() is immediately persisted to the ChatMemoryStore.
 *
 * This is used when ChatMemoryFlushStrategy.IMMEDIATE is configured.
 * There is no buffering - commit() is a no-op because writing messages
 * to the store already happened on each add().
 * </pre>
 */
class ImmediateFlushChatMemory implements CommittableChatMemory {

    private final ChatMemory delegate;

    ImmediateFlushChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        delegate.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return delegate.messages();
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public void commit() {
        // no-op - messages are already persisted on add()
    }
}
