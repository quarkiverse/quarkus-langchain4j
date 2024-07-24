package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;

class DefaultCommittableChatMemory implements CommittableChatMemory {

    private final ChatMemory delegate;
    private final List<ChatMessage> newMessages;

    public DefaultCommittableChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
        this.newMessages = new ArrayList<>(delegate.messages());
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> systemMessage = findSystemMessage(newMessages);
            if (systemMessage.isPresent()) {
                if (systemMessage.get().equals(message)) {
                    return; // do not add the same system message
                } else {
                    newMessages.remove(systemMessage.get()); // need to replace existing system message
                }
            }
        }
        newMessages.add(message);
    }

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findAny();
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(newMessages);
    }

    // the following operations are terminal

    @Override
    public void clear() {
        newMessages.clear();
        delegate.clear();
    }

    @Override
    public void commit() {
        delegate.clear(); // remove the original messages as this class keeps the entire state
        for (ChatMessage newMessage : newMessages) {
            delegate.add(newMessage);
        }
    }
}
