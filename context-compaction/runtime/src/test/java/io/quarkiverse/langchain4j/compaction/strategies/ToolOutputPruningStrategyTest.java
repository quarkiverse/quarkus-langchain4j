package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.Fixtures;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;

class ToolOutputPruningStrategyTest {

    private final ToolOutputArchive archive = new ToolOutputArchive();

    private ToolOutputPruningStrategy strategy(int keepLastTurns, int minChars, int previewChars) {
        return new ToolOutputPruningStrategy(new ToolOutputPruningStrategy.Options(keepLastTurns, minChars,
                previewChars), archive);
    }

    private static List<ToolExecutionResultMessage> toolResults(List<ChatMessage> messages) {
        return messages.stream().filter(m -> m instanceof ToolExecutionResultMessage)
                .map(m -> (ToolExecutionResultMessage) m).toList();
    }

    @Test
    void prunesOldOutputsAndArchivesThem() {
        List<ChatMessage> history = Fixtures.conversation(6);
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = strategy(2, 200, 30).compact(history, context);

        List<ToolExecutionResultMessage> results = toolResults(view);
        // turns 0, 2, 4 have tool calls; turn 4 is within the last 2 turns
        assertThat(results).hasSize(3);
        assertThat(CompactionMessages.kind(results.get(0))).isEqualTo(CompactionMessages.KIND_PRUNED);
        assertThat(results.get(0).text()).contains("Archived as 'call-0'").contains("Preview: order 1000 shipped");
        assertThat(results.get(0).id()).isEqualTo("call-0");
        assertThat(CompactionMessages.kind(results.get(2))).isNull();
        assertThat(archive.get("test", "call-0")).hasValueSatisfying(v -> assertThat(v).startsWith("order 1000"));
        assertThat(context.notes).anyMatch(n -> n.contains("pruned 2 tool outputs"));
        // only tool outputs change: same number of messages
        assertThat(view).hasSameSizeAs(history);
    }

    @Test
    void keepsShortOutputs() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("is the API up?"));
        history.add(AiMessage.from(Fixtures.request("c1", "health", "{}")));
        history.add(ToolExecutionResultMessage.from("c1", "health", "UP"));
        history.add(AiMessage.from("It is up."));
        history.addAll(Fixtures.conversation(3).subList(1, 10));

        List<ChatMessage> view = strategy(1, 200, 30).compact(history, Fixtures.context(10_000));
        assertThat(view).contains(history.get(3));
    }

    @Test
    void prunesEveryOutputOfAParallelCall() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("compare my two orders"));
        history.add(AiMessage.from(Fixtures.request("a", "lookupOrder", "{\"order\":1}"),
                Fixtures.request("b", "lookupOrder", "{\"order\":2}")));
        history.add(ToolExecutionResultMessage.from("a", "lookupOrder", "order 1 " + "x".repeat(500)));
        history.add(ToolExecutionResultMessage.from("b", "lookupOrder", "order 2 " + "y".repeat(500)));
        history.add(AiMessage.from("Both shipped."));
        history.add(UserMessage.from("thanks"));
        history.add(AiMessage.from("You're welcome."));

        List<ChatMessage> view = strategy(1, 200, 30).compact(history, Fixtures.context(10_000));
        assertThat(toolResults(view)).allMatch(r -> CompactionMessages.KIND_PRUNED.equals(CompactionMessages.kind(r)));
        assertThat(archive.get("test", "a")).hasValueSatisfying(v -> assertThat(v).startsWith("order 1"));
        assertThat(archive.get("test", "b")).hasValueSatisfying(v -> assertThat(v).startsWith("order 2"));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void isIdempotent() {
        List<ChatMessage> history = Fixtures.conversation(8);
        ToolOutputPruningStrategy strategy = strategy(2, 200, 30);
        List<ChatMessage> once = strategy.compact(history, Fixtures.context(10_000));
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> twice = strategy.compact(once, context);
        // pruned stubs are derived messages: they are never pruned again
        assertThat(twice).isEqualTo(once);
        assertThat(context.notes).isEmpty();
    }

    @Test
    void archivesOutputsWithoutIdUnderTheToolName() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("search the knowledge base"));
        history.add(AiMessage.from(Fixtures.request(null, "search", "{\"q\":\"descaling\"}")));
        String output = "article: descaling " + "z".repeat(400);
        history.add(ToolExecutionResultMessage.from((String) null, "search", output));
        history.add(AiMessage.from("Descale monthly."));
        history.add(UserMessage.from("thanks"));
        history.add(AiMessage.from("You're welcome."));

        List<ChatMessage> view = strategy(1, 200, 30).compact(history, Fixtures.context(10_000));
        String stub = toolResults(view).get(0).text();
        String key = stub.substring(stub.indexOf("Archived as '") + 13, stub.lastIndexOf('\''));
        assertThat(key).startsWith("search#");
        assertThat(archive.get("test", key)).contains(output);
    }
}
