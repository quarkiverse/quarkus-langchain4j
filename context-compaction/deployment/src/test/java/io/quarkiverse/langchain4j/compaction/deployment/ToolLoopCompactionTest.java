package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.runtime.TokenCounterProducer;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryFlushStrategy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * An agent that calls a tool many times within a single AI service invocation: every request sent to the model must be
 * compacted, not only the first one, whatever the chat memory flush strategy. The store must still receive the full
 * history at the end of the invocation.
 */
class ToolLoopCompactionTest {

    static final int TOOL_CALLS = 8;
    static final int OUTPUT_CHARS = 2000;
    static final int CONTEXT_WINDOW = 2000;

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(DeferredAgent.class, ImmediateAgent.class, ImmediateFlush.class,
                    FakeModel.class, FakeModelSupplier.class, Lookup.class, ReportCollector.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.strategy", "token-window")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.context-window", String.valueOf(CONTEXT_WINDOW))
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.reserved-completion-tokens", "0");

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class, tools = Lookup.class)
    interface DeferredAgent {
        @SystemMessage("You investigate incidents.")
        String investigate(@MemoryId String id, @UserMessage String question);
    }

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class, tools = Lookup.class, chatMemoryFlushStrategySupplier = ImmediateFlush.class)
    interface ImmediateAgent {
        @SystemMessage("You investigate incidents.")
        String investigate(@MemoryId String id, @UserMessage String question);
    }

    public static class ImmediateFlush implements Supplier<ChatMemoryFlushStrategy> {
        @Override
        public ChatMemoryFlushStrategy get() {
            return ChatMemoryFlushStrategy.IMMEDIATE;
        }
    }

    @ApplicationScoped
    public static class Lookup {
        final AtomicInteger calls = new AtomicInteger();

        @Tool("Looks up the next page of logs")
        public String nextLogPage() {
            int page = calls.incrementAndGet();
            return ("page " + page + ": ").repeat(OUTPUT_CHARS / 8);
        }

        // accessed through methods: fields of a client proxy are not those of the bean instance
        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }

    /**
     * Calls the tool until it was called {@value #TOOL_CALLS} times, then answers. Records every request.
     */
    @Singleton
    public static class FakeModel implements ChatModel {
        final List<List<ChatMessage>> requests = new CopyOnWriteArrayList<>();

        @Inject
        Lookup lookup;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request.messages());
            int calls = lookup.calls();
            if (calls < TOOL_CALLS) {
                return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-" + calls).name("nextLogPage").arguments("{}").build())).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("The incident is caused by a full disk.")).build();
        }
    }

    public static class FakeModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return io.quarkus.arc.Arc.container().instance(FakeModel.class).get();
        }
    }

    @ApplicationScoped
    public static class ReportCollector {
        final List<CompactionReport> reports = new CopyOnWriteArrayList<>();

        void onReport(@Observes CompactionReport report) {
            reports.add(report);
        }

        List<CompactionReport> reports() {
            return reports;
        }

        void clear() {
            reports.clear();
        }
    }

    @Inject
    DeferredAgent deferred;

    @Inject
    ImmediateAgent immediate;

    @Inject
    FakeModel model;

    @Inject
    Lookup lookup;

    @Inject
    ChatMemoryStore store;

    @Inject
    ReportCollector collector;

    @BeforeEach
    void reset() {
        model.requests.clear();
        lookup.reset();
        collector.clear();
    }

    @Test
    @ActivateRequestContext
    void compactsEveryRequestOfTheToolLoopWithDeferredFlush() {
        assertThat(deferred.investigate("deferred", "Why is the payment service down?"))
                .contains("full disk");
        verify("deferred");
    }

    @Test
    @ActivateRequestContext
    void compactsEveryRequestOfTheToolLoopWithImmediateFlush() {
        assertThat(immediate.investigate("immediate", "Why is the payment service down?"))
                .contains("full disk");
        verify("immediate");
    }

    private void verify(String memoryId) {
        assertThat(lookup.calls()).isEqualTo(TOOL_CALLS);
        assertThat(model.requests).hasSize(TOOL_CALLS + 1);

        // without compaction, the last request would hold all the tool outputs (about 4000 tokens)
        TokenCounterProducer.HeuristicTokenCounter counter = new TokenCounterProducer.HeuristicTokenCounter();
        int trigger = (int) (CONTEXT_WINDOW * 0.8);
        for (List<ChatMessage> request : model.requests) {
            assertThat(counter.count(request)).isLessThanOrEqualTo(trigger);
            // the question is never dropped, even though all the tool calls belong to the same turn
            assertThat(request).anyMatch(m -> m instanceof dev.langchain4j.data.message.UserMessage);
        }
        assertThat(collector.reports()).isNotEmpty().allMatch(r -> r.memoryId().equals(memoryId));

        // the store has the full history: system, question, 8 x (tool call + result), answer
        List<ChatMessage> history = store.getMessages(memoryId);
        assertThat(history).hasSize(2 + 2 * TOOL_CALLS + 1);
        assertThat(history).noneMatch(CompactionMessages::isDerived);
        assertThat(history.stream().filter(m -> m instanceof ToolExecutionResultMessage)).hasSize(TOOL_CALLS);
    }
}
