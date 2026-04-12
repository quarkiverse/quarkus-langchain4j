package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;

/**
 * <pre>
 * A CommittableChatMemory that persists every change to the real ChatMemory
 * immediately, so the ChatMemoryStore always reflects the latest state.
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
        if (message instanceof SystemMessage) {
            List<ChatMessage> messages = new ArrayList<>(delegate.messages());
            Optional<SystemMessage> systemMessage = findSystemMessage(messages);
            if (systemMessage.isPresent()) {
                if (systemMessage.get().equals(message)) {
                    return; // do not add the same system message
                } else {
                    messages.remove(systemMessage.get()); // need to replace existing system message
                }
            }
            messages.add(0, message); // the system message must be in the first position
            delegate.set(messages);
        } else {
            delegate.add(message);
        }
    }

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream().filter(message -> message instanceof SystemMessage).map(message -> (SystemMessage) message).findAny();
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
