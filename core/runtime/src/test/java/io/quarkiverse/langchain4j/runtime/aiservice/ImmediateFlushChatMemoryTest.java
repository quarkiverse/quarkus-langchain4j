package io.quarkiverse.langchain4j.runtime.aiservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

class ImmediateFlushChatMemoryTest {

    @Test
    void shouldIgnoreDuplicateSystemMessage() {
        TrackingChatMemoryStore store = new TrackingChatMemoryStore();
        ImmediateFlushChatMemory memory = new ImmediateFlushChatMemory(createDelegate(store));

        memory.add(SystemMessage.from("system"));
        memory.add(SystemMessage.from("system"));

        assertEquals(1, store.updateCount());
        assertEquals(1, memory.messages().size());
        assertEquals("system", assertInstanceOf(SystemMessage.class, memory.messages().get(0)).text());
    }

    @Test
    void shouldReplaceExistingSystemMessageAndKeepItFirst() {
        TrackingChatMemoryStore store = new TrackingChatMemoryStore();
        ImmediateFlushChatMemory memory = new ImmediateFlushChatMemory(createDelegate(store));

        memory.add(UserMessage.from("hello"));
        memory.add(SystemMessage.from("system-1"));
        memory.add(UserMessage.from("again"));
        memory.add(SystemMessage.from("system-2"));

        List<ChatMessage> messages = memory.messages();
        assertEquals(3, messages.size());
        assertEquals("system-2", assertInstanceOf(SystemMessage.class, messages.get(0)).text());
        assertEquals("hello", assertInstanceOf(UserMessage.class, messages.get(1)).singleText());
        assertEquals("again", assertInstanceOf(UserMessage.class, messages.get(2)).singleText());
        assertEquals(messages, store.messages());
    }

    private static MessageWindowChatMemory createDelegate(TrackingChatMemoryStore store) {
        return MessageWindowChatMemory.builder()
                .id("test-memory")
                .maxMessages(10)
                .chatMemoryStore(store)
                .build();
    }

    private static class TrackingChatMemoryStore implements ChatMemoryStore {

        private final AtomicInteger updateCount = new AtomicInteger();
        private List<ChatMessage> messages = new ArrayList<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(messages);
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            updateCount.incrementAndGet();
            this.messages = new ArrayList<>(messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            messages = new ArrayList<>();
        }

        int updateCount() {
            return updateCount.get();
        }

        List<ChatMessage> messages() {
            return new ArrayList<>(messages);
        }
    }
}
