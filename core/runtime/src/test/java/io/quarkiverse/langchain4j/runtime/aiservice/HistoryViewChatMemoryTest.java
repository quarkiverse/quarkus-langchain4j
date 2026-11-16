package io.quarkiverse.langchain4j.runtime.aiservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.memory.HistoryViewChatMemory;

/**
 * The committable chat memories buffer and write the full history of a {@link HistoryViewChatMemory}, and send its view
 * of the buffer to the model.
 */
class HistoryViewChatMemoryTest {

    @Test
    void deferredMemoryBuffersTheHistoryAndComputesTheViewOfTheBuffer() {
        LastTwoMemory delegate = new LastTwoMemory();
        delegate.history.addAll(List.of(SystemMessage.from("system"), UserMessage.from("q1"), AiMessage.from("a1"),
                UserMessage.from("q2"), AiMessage.from("a2")));
        DefaultCommittableChatMemory memory = new DefaultCommittableChatMemory(delegate);

        memory.add(UserMessage.from("q3"));
        // the view of the buffer, which includes the message that is not stored yet
        assertEquals(List.of(SystemMessage.from("system"), AiMessage.from("a2"), UserMessage.from("q3")),
                memory.messages());

        memory.add(AiMessage.from("tool call"));
        memory.add(AiMessage.from("a3"));
        assertEquals(List.of(SystemMessage.from("system"), AiMessage.from("tool call"), AiMessage.from("a3")),
                memory.messages());
        // the view is cached until the buffer changes
        memory.messages();
        assertEquals(2, delegate.viewCalls);

        memory.replaceLastAiMessage(AiMessage.from("a3 (validated)"));
        assertEquals(AiMessage.from("a3 (validated)"), memory.messages().get(2));

        memory.commit();
        assertFalse(delegate.setCalled);
        assertEquals(8, delegate.history.size());
        assertEquals(UserMessage.from("q1"), delegate.history.get(1));
        assertEquals(AiMessage.from("a3 (validated)"), delegate.history.get(7));
    }

    @Test
    void immediateMemoryRewritesTheHistoryNotTheView() {
        LastTwoMemory delegate = new LastTwoMemory();
        delegate.history.addAll(List.of(SystemMessage.from("system"), UserMessage.from("q1"), AiMessage.from("a1"),
                UserMessage.from("q2"), AiMessage.from("a2")));
        ImmediateFlushChatMemory memory = new ImmediateFlushChatMemory(delegate);

        memory.replaceLastAiMessage(AiMessage.from("a2 (validated)"));
        memory.add(SystemMessage.from("new system"));

        assertFalse(delegate.setCalled);
        assertEquals(List.of(SystemMessage.from("new system"), UserMessage.from("q1"), AiMessage.from("a1"),
                UserMessage.from("q2"), AiMessage.from("a2 (validated)")), delegate.history);
    }

    /**
     * Stores everything, sends the system message and the last two other messages.
     */
    static class LastTwoMemory implements HistoryViewChatMemory {
        final List<ChatMessage> history = new ArrayList<>();
        int viewCalls;
        boolean setCalled;

        @Override
        public Object id() {
            return "test";
        }

        @Override
        public List<ChatMessage> history() {
            return new ArrayList<>(history);
        }

        @Override
        public List<ChatMessage> view(List<ChatMessage> messages) {
            viewCalls++;
            List<ChatMessage> result = new ArrayList<>(messages.stream().filter(m -> m instanceof SystemMessage).toList());
            List<ChatMessage> others = messages.stream().filter(m -> !(m instanceof SystemMessage)).toList();
            result.addAll(others.subList(Math.max(0, others.size() - 2), others.size()));
            return result;
        }

        @Override
        public void setHistory(List<ChatMessage> messages) {
            history.clear();
            history.addAll(messages);
        }

        @Override
        public void add(ChatMessage message) {
            history.add(message);
        }

        @Override
        public void set(Iterable<ChatMessage> messages) {
            setCalled = true;
        }

        @Override
        public List<ChatMessage> messages() {
            return view(history);
        }

        @Override
        public void clear() {
            history.clear();
        }
    }
}
