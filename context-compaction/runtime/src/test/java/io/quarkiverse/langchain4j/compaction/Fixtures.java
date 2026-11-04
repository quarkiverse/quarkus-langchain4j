package io.quarkiverse.langchain4j.compaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.runtime.TokenCounterProducer;

public final class Fixtures {

    public static final SystemMessage SYSTEM = SystemMessage.from("You are a support agent for ACME.");

    private Fixtures() {
    }

    /**
     * A support conversation: each turn is a user question, a tool exchange with a large output (every other turn)
     * and an answer.
     */
    public static List<ChatMessage> conversation(int turns) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SYSTEM);
        for (int i = 0; i < turns; i++) {
            messages.add(UserMessage.from("Question " + i + ": what is the status of order " + (1000 + i) + "?"));
            if (i % 2 == 0) {
                ToolExecutionRequest request = ToolExecutionRequest.builder().id("call-" + i).name("lookupOrder")
                        .arguments("{\"order\":" + (1000 + i) + "}").build();
                messages.add(AiMessage.from(request));
                messages.add(ToolExecutionResultMessage.from(request, "order " + (1000 + i) + " shipped. "
                        + "x".repeat(800)));
            }
            messages.add(AiMessage.from("Order " + (1000 + i) + " has shipped and arrives on day " + i + "."));
        }
        return messages;
    }

    public static ToolExecutionRequest request(String id, String name, String args) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(args).build();
    }

    public static TestContext context(int contextWindow) {
        return context(new TokenBudget(contextWindow, 0, 1.0, 1.0));
    }

    public static TestContext context(TokenBudget budget) {
        return new TestContext(budget);
    }

    public static final class TestContext implements CompactionContext {
        private final TokenBudget budget;
        private final TokenCounter counter = new TokenCounterProducer.HeuristicTokenCounter();
        private final Map<String, Map<String, Object>> state = new HashMap<>();
        public final List<String> notes = new ArrayList<>();
        public final Map<ChatMessage, Instant> timestamps = new IdentityHashMap<>();
        public Instant now = Instant.parse("2026-10-20T12:00:00Z");

        TestContext(TokenBudget budget) {
            this.budget = budget;
        }

        @Override
        public Object memoryId() {
            return "test";
        }

        @Override
        public TokenBudget budget() {
            return budget;
        }

        @Override
        public TokenCounter tokenCounter() {
            return counter;
        }

        @Override
        public Optional<Instant> timestamp(ChatMessage message) {
            return Optional.ofNullable(timestamps.get(message));
        }

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public Map<String, Object> state(String owner) {
            return state.computeIfAbsent(owner, k -> new HashMap<>());
        }

        @Override
        public void note(String owner, String note) {
            notes.add(owner + ": " + note);
        }
    }

    /**
     * A summarizer that records its calls and returns a deterministic summary.
     */
    public static final class RecordingSummarizer implements Summarizer {
        public final List<Integer> calls = new ArrayList<>();
        public final List<String> previous = new ArrayList<>();

        @Override
        public String summarize(String previousSummary, List<ChatMessage> messages, int maxWords) {
            calls.add(messages.size());
            previous.add(previousSummary);
            return (previousSummary == null ? "" : previousSummary + " + ") + messages.size() + " messages";
        }
    }
}
