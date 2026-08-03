package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

/**
 * A CommittableChatMemory that tracks messages in a local list without
 * any persistent backing store. Used when no ChatMemoryProvider is
 * configured, so messages are available within a single invocation
 * (e.g. for tool-loop retries and guardrail reprompts) but are not
 * persisted across calls.
 */
class LocalCommittableChatMemory implements CommittableChatMemory {

    private final List<ChatMessage> messages = new ArrayList<>();

    @Override
    public Object id() {
        return "default";
    }

    @Override
    public void add(ChatMessage message) {
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> existing = messages.stream()
                    .filter(m -> m instanceof SystemMessage)
                    .map(m -> (SystemMessage) m)
                    .findAny();
            if (existing.isPresent()) {
                if (existing.get().equals(message)) {
                    return;
                }
                messages.remove(existing.get());
            }
            messages.add(0, message);
        } else {
            messages.add(message);
        }
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    @Override
    public void replaceLastAiMessage(AiMessage newAiMessage) {
        ListIterator<ChatMessage> it = messages.listIterator(messages.size());
        while (it.hasPrevious()) {
            if (it.previous() instanceof AiMessage) {
                it.set(newAiMessage);
                return;
            }
        }
    }

    @Override
    public void commit() {
    }
}
