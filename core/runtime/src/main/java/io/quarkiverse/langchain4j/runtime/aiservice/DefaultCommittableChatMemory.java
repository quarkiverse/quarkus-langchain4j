package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import io.quarkiverse.langchain4j.memory.HistoryViewChatMemory;

class DefaultCommittableChatMemory implements CommittableChatMemory {

    private final ChatMemory delegate;
    /**
     * Not {@code null} when the delegate sends a view of its history to the model: the buffer then holds the full
     * history, and the view is computed from it.
     */
    private final HistoryViewChatMemory historyView;
    private final List<ChatMessage> newMessages;
    /**
     * The view of {@link #newMessages}, computed lazily and reset whenever the buffer changes.
     */
    private List<ChatMessage> view;

    public DefaultCommittableChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
        this.historyView = delegate instanceof HistoryViewChatMemory h ? h : null;
        this.newMessages = historyView != null ? new ArrayList<>(historyView.history())
                : new ArrayList<>(delegate.messages());
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        view = null;
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> systemMessage = findSystemMessage(newMessages);
            if (systemMessage.isPresent()) {
                if (systemMessage.get().equals(message)) {
                    return; // do not add the same system message
                } else {
                    newMessages.remove(systemMessage.get()); // need to replace existing system message
                }
            }
            newMessages.add(0, message); // the system message must be in the first position
        } else {
            newMessages.add(message);
        }
    }

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findAny();
    }

    @Override
    public List<ChatMessage> messages() {
        if (historyView == null) {
            return new ArrayList<>(newMessages);
        }
        if (view == null) {
            view = List.copyOf(historyView.view(List.copyOf(newMessages)));
        }
        return new ArrayList<>(view);
    }

    // the following operations are terminal

    @Override
    public void clear() {
        view = null;
        newMessages.clear();
        delegate.clear();
    }

    @Override
    public void replaceLastAiMessage(AiMessage newAiMessage) {
        view = null;
        ListIterator<ChatMessage> it = newMessages.listIterator(newMessages.size());
        while (it.hasPrevious()) {
            if (it.previous() instanceof AiMessage) {
                it.set(newAiMessage);
                return;
            }
        }
    }

    /**
     * Commits all buffered messages to the delegate by calling {@link ChatMemory#set}.
     */
    @Override
    public void commit() {
        if (historyView != null) {
            historyView.setHistory(newMessages);
        } else {
            delegate.set(newMessages);
        }
    }
}
