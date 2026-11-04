package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.runtime.CompactingChatMemoryProvider;
import io.quarkiverse.langchain4j.compaction.strategies.Strategies;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;

class CompactionWiringTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Assistant.class, FakeModel.class, FakeModelSupplier.class,
                    ReportCollector.class, KeepLastTwo.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.strategy", "pipeline")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.context-window", "600")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.reserved-completion-tokens", "100")
            .overrideConfigKey("quarkus.langchain4j.compaction.summary.keep-last-turns", "2");

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class)
    interface Assistant {
        @SystemMessage("You are a helpful assistant.")
        String chat(@MemoryId String id, @UserMessage String question);
    }

    /**
     * Answers with a long text and records the size of each request, so the test sees what the model received.
     */
    @Singleton
    public static class FakeModel implements ChatModel {
        final List<Integer> requestSizes = new ArrayList<>();
        final List<List<ChatMessage>> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requestSizes.add(request.messages().size());
            requests.add(request.messages());
            String last = CompactionMessages.text(request.messages().get(request.messages().size() - 1));
            if (last.startsWith("Conversation to summarize") || last.startsWith("Existing summary")) {
                return ChatResponse.builder().aiMessage(AiMessage.from("- the user asked many questions")).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("Answer: " + "lorem ipsum ".repeat(20))).build();
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
        final List<CompactionReport> reports = new ArrayList<>();

        List<CompactionReport> reports() {
            return reports;
        }

        void onReport(@Observes CompactionReport report) {
            reports.add(report);
        }
    }

    /**
     * A custom strategy contributed by the application.
     */
    @Singleton
    @Identifier("keep-last-two")
    public static class KeepLastTwo implements CompactionStrategy {
        @Override
        public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
            List<ChatMessage> all = conversation.toMessages();
            List<ChatMessage> result = new ArrayList<>(conversation.system());
            List<ChatMessage> tail = all.subList(Math.max(0, all.size() - 2), all.size());
            for (ChatMessage m : tail) {
                if (!result.contains(m)) {
                    result.add(m);
                }
            }
            return result;
        }
    }

    @Inject
    Assistant assistant;

    @Inject
    ChatMemoryProvider provider;

    @Inject
    ChatMemoryStore store;

    @Inject
    FakeModel model;

    @Inject
    ReportCollector collector;

    @Inject
    CompactionInspector inspector;

    @Inject
    @Any
    Instance<CompactionStrategy> strategies;

    @Test
    void replacesTheDefaultChatMemoryProvider() {
        assertThat(provider).isInstanceOf(CompactingChatMemoryProvider.class);
    }

    @Test
    void exposesBuiltInAndCustomStrategies() {
        for (String id : List.of(Strategies.MESSAGE_WINDOW, Strategies.TOKEN_WINDOW, Strategies.TOOL_OUTPUT_PRUNING,
                Strategies.DUPLICATE_COLLAPSE, Strategies.ROLLING_SUMMARY, Strategies.ANCHORED_SUMMARY,
                Strategies.TIME_DECAY, Strategies.RELEVANCE, Strategies.PIPELINE, "keep-last-two")) {
            assertThat(strategies.select(Identifier.Literal.of(id)).isResolvable()).as(id).isTrue();
        }
    }

    @Test
    @ActivateRequestContext
    void compactsTheConversationSentToTheModel() {
        for (int i = 0; i < 15; i++) {
            assistant.chat("conv-1", "Question " + i + ": " + "please tell me more ".repeat(5));
        }
        // the store has the full history: system + 15 x (user + ai)
        assertThat(store.getMessages("conv-1")).hasSize(31);
        assertThat(store.getMessages("conv-1")).noneMatch(CompactionMessages::isDerived);

        // the model received a bounded context
        List<ChatMessage> lastRequest = model.requests.get(model.requests.size() - 1);
        assertThat(lastRequest.size()).isLessThan(31);
        assertThat(lastRequest.get(0)).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
        assertThat(lastRequest).anyMatch(CompactionMessages::isSummary);

        assertThat(inspector.reports()).isNotEmpty();
        assertThat(collector.reports()).isNotEmpty();
        assertThat(collector.reports().get(collector.reports().size() - 1).strategy()).isEqualTo("pipeline");
        assertThat(inspector.snapshot("conv-1")).isPresent();
    }
}
