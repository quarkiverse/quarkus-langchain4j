package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionProfiles;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Two AI services of the same application, one with the default profile and one with its own: each gets the strategy,
 * budget and options of its profile.
 */
class CompactionProfilesTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Assistant.class, Triage.class, FakeModel.class,
                    FakeModelSupplier.class, ReportCollector.class))
            // default profile: summarize, with a large budget
            .overrideConfigKey("quarkus.langchain4j.compaction.strategy", "rolling-summary")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.context-window", "600")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.reserved-completion-tokens", "100")
            .overrideConfigKey("quarkus.langchain4j.compaction.summary.keep-last-turns", "2")
            // triage profile: keep the last messages only, and compact much earlier
            .overrideConfigKey("quarkus.langchain4j.compaction.triage.strategy", "message-window")
            .overrideConfigKey("quarkus.langchain4j.compaction.triage.message-window.max-messages", "4")
            .overrideConfigKey("quarkus.langchain4j.compaction.triage.budget.context-window", "300")
            .overrideConfigKey("quarkus.langchain4j.compaction.triage.budget.reserved-completion-tokens", "50");

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class)
    interface Assistant {
        @SystemMessage("You are a helpful assistant.")
        String chat(@MemoryId String id, @UserMessage String question);
    }

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class)
    @CompactionProfile("triage")
    interface Triage {
        @SystemMessage("You route support requests.")
        String chat(@MemoryId String id, @UserMessage String question);
    }

    @Singleton
    public static class FakeModel implements ChatModel {
        final List<List<ChatMessage>> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            String last = CompactionMessages.text(request.messages().get(request.messages().size() - 1));
            if (last.startsWith("Conversation to summarize") || last.startsWith("Existing summary")) {
                return ChatResponse.builder().aiMessage(AiMessage.from("- the user asked many questions")).build();
            }
            requests.add(request.messages());
            return ChatResponse.builder().aiMessage(AiMessage.from("Answer: " + "lorem ipsum ".repeat(20))).build();
        }

        List<ChatMessage> lastRequestStartingWith(String system) {
            for (int i = requests.size() - 1; i >= 0; i--) {
                if (CompactionMessages.text(requests.get(i).get(0)).startsWith(system)) {
                    return requests.get(i);
                }
            }
            throw new java.util.NoSuchElementException();
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

    @Inject
    Assistant assistant;

    @Inject
    Triage triage;

    @Inject
    ChatMemoryStore store;

    @Inject
    FakeModel model;

    @Inject
    ReportCollector collector;

    @Inject
    CompactionProfiles profiles;

    @Test
    @ActivateRequestContext
    void eachServiceUsesItsProfile() {
        for (int i = 0; i < 12; i++) {
            assistant.chat("a-1", "Question " + i + ": " + "please tell me more ".repeat(5));
            triage.chat("t-1", "Request " + i + ": " + "please route this ".repeat(5));
        }
        // both stores keep the full history
        assertThat(store.getMessages("a-1")).hasSize(25);
        assertThat(store.getMessages("t-1")).hasSize(25);

        // the default profile summarizes
        List<ChatMessage> assistantView = model.lastRequestStartingWith("You are a helpful assistant.");
        assertThat(assistantView).anyMatch(CompactionMessages::isSummary);

        // the triage profile keeps the system message and the last 4 messages, plus the new question
        List<ChatMessage> triageView = model.lastRequestStartingWith("You route support requests.");
        assertThat(triageView).noneMatch(CompactionMessages::isSummary);
        assertThat(triageView.size()).isLessThanOrEqualTo(6);

        assertThat(collector.reports()).anyMatch(r -> r.profile().equals("default")
                && r.strategy().equals("rolling-summary") && r.memoryId().equals("a-1"));
        assertThat(collector.reports()).anyMatch(r -> r.profile().equals("triage")
                && r.strategy().equals("message-window") && r.memoryId().equals("t-1"));
        assertThat(collector.reports()).filteredOn(r -> r.profile().equals("triage"))
                .allMatch(r -> r.budget().contextWindow() == 300);
    }

    @Test
    void exposesTheProfiles() {
        assertThat(profiles.defaultProvider().components().strategyId()).isEqualTo("rolling-summary");
        assertThat(profiles.provider("triage").components().strategyId()).isEqualTo("message-window");
        assertThat(profiles.provider("triage").components().budget().contextWindow()).isEqualTo(300);
    }
}
