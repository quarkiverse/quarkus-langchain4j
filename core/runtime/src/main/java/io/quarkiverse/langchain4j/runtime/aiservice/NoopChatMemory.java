package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Collections;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

/**
 * An implementation of {@link ChatMemory} that does nothing.
 * This is useful for simplifying the AiService code.
 */
public class NoopChatMemory implements CommittableChatMemory {
    @Override
    public Object id() {
        return "default";
    }

    @Override
    public void add(ChatMessage message) {

    }

    @Override
    public List<ChatMessage> messages() {
        return Collections.emptyList();
    }

    @Override
    public void clear() {

    }

    @Override
    public void commit() {

    }
}
