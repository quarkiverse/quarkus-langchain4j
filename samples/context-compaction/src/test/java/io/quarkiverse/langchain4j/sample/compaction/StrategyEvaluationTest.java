package io.quarkiverse.langchain4j.sample.compaction;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.AnswerModel;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluation;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.Result;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.JudgeModel;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Compares the compaction strategies one by one, and the default pipeline, on the outage conversation: token savings,
 * recall, and the time spent compacting. One profile per strategy is configured in {@code application.properties}
 * ({@code %test.quarkus.langchain4j.compaction.profiles.*}), all with the same 6k-token budget.
 * <p>
 * It makes several hundred model calls, so it only runs when asked:
 * {@code mvn test -Dtest=StrategyEvaluationTest -Devaluate-strategies=true}. Reports are written to
 * {@code target/compaction-evaluation}.
 * <p>
 * {@code duplicate-collapse} is not evaluated alone: it only removes identical tool calls, so on its own it cannot bring
 * this conversation under the usable window (the replay would fail with a {@code CompactionException}). It runs as a
 * step of the pipeline.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QUARKUS_LANGCHAIN4J_OPENAI_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "evaluate-strategies", matches = "true")
class StrategyEvaluationTest {

    @Inject
    @AnswerModel
    ChatModel assistant;

    @Inject
    @ModelName("judge")
    @JudgeModel
    ChatModel judge;

    // makes Quarkus create the default embedding model, used by the relevance strategy
    @Inject
    EmbeddingModel embeddings;

    @Inject
    Latencies latencies;

    @CompactionEvaluation(conversation = "conversations/outage.jsonl", questions = "conversations/outage.questions.json", profiles = {
            "token-window", "message-window", "tool-output-pruning", "rolling-summary", "anchored-summary", "time-decay",
            "relevance", "pipeline" }, repeats = 3)
    void outage(CompactionEvaluationReport report) throws IOException {
        Path file = Path.of("target/compaction-evaluation/StrategyEvaluationTest-summary.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, summary(report));
        // otherwise the questions, not the strategies, are the problem
        assertTrue(report.recall(CompactionEvaluationReport.FULL_HISTORY) >= 0.9, "full-history recall");
    }

    private String summary(CompactionEvaluationReport report) {
        StringBuilder md = new StringBuilder("# Compaction strategies on the outage conversation\n\n");
        md.append("Budget: 6000-token window, 1000 reserved, trigger 80%, target 60%. Latency is the time spent")
                .append(" compacting while replaying the conversation, including summarizer and embedding calls.\n\n");
        md.append("| Strategy | Token savings | Recall | Compactions | Latency total | Latency max |\n");
        md.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (Result r : report.results()) {
            List<CompactionReport> runs = latencies.of(r.name());
            long total = runs.stream().mapToLong(c -> c.duration().toMillis()).sum();
            long max = runs.stream().mapToLong(c -> c.duration().toMillis()).max().orElse(0);
            md.append(String.format(Locale.ROOT, "| %s | %.0f%% | %.0f%% | %d | %d ms | %d ms |%n", r.name(),
                    r.savings() * 100, r.recall() * 100, r.compactions(), total, max));
        }
        // what each strategy did, to explain the numbers (e.g. which pipeline steps ran, which similarity relevance used)
        md.append("\n## Last compaction notes\n\n");
        for (Result r : report.results()) {
            List<CompactionReport> runs = latencies.of(r.name());
            if (!runs.isEmpty()) {
                md.append("- **").append(r.name()).append("**: ").append(runs.getLast().notes()).append('\n');
            }
        }
        return md.toString();
    }

    /**
     * Collects the compaction reports of the evaluation replays, per profile.
     */
    @ApplicationScoped
    static class Latencies {

        private final Map<String, List<CompactionReport>> reports = new ConcurrentHashMap<>();

        void onCompaction(@Observes CompactionReport report) {
            if (report.memoryId().toString().startsWith("compaction-evaluation/")) {
                reports.computeIfAbsent(report.profile(), p -> new CopyOnWriteArrayList<>()).add(report);
            }
        }

        List<CompactionReport> of(String profile) {
            return reports.getOrDefault(profile, List.of());
        }
    }
}
