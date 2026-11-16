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

class DuplicateCollapseStrategyTest {

    private static final String OUTPUT = "status: DOWN, last check failed, retry scheduled. " + "details ".repeat(20);

    private static void poll(List<ChatMessage> history, String id, String arguments, String output) {
        history.add(AiMessage.from(Fixtures.request(id, "health", arguments)));
        history.add(ToolExecutionResultMessage.from(id, "health", output));
    }

    private static List<ChatMessage> polling(String... outputs) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("Is the API up?"));
        for (int i = 0; i < outputs.length; i++) {
            poll(history, "poll-" + i, "{}", outputs[i]);
        }
        history.add(AiMessage.from("The API is still down."));
        return history;
    }

    @Test
    void keepsTheLastOfIdenticalCalls() {
        List<ChatMessage> history = polling(OUTPUT, OUTPUT, OUTPUT);
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = new DuplicateCollapseStrategy().compact(history, context);
        // tool request ids differ, but name, arguments and output are identical
        assertThat(view).hasSize(5);
        assertThat(((ToolExecutionResultMessage) view.get(3)).id()).isEqualTo("poll-2");
        assertThat(((ToolExecutionResultMessage) view.get(3)).text()).contains("Collapsed 2 earlier identical calls");
        assertThat(CompactionMessages.kind(view.get(3))).isEqualTo(CompactionMessages.KIND_COLLAPSED);
        assertThat(context.notes).anyMatch(n -> n.contains("collapsed 2 repeated tool calls"));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void keepsCallsWhoseOutputChanged() {
        List<ChatMessage> history = polling(OUTPUT, OUTPUT, "status: UP");
        List<ChatMessage> view = new DuplicateCollapseStrategy().compact(history, Fixtures.context(10_000));
        // the two DOWN polls collapse, the UP poll is kept
        assertThat(view).hasSize(7);
        assertThat(view).contains(history.get(6), history.get(7));
    }

    @Test
    void keepsCallsWithDifferentArguments() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("check both regions"));
        poll(history, "a", "{\"region\":\"eu\"}", OUTPUT);
        poll(history, "b", "{\"region\":\"us\"}", OUTPUT);
        assertThat(new DuplicateCollapseStrategy().compact(history, Fixtures.context(10_000))).isEqualTo(history);
    }

    @Test
    void keepsCallsCarryingTextOrSeveralRequests() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("Is the API up?"));
        for (int i = 0; i < 2; i++) {
            history.add(AiMessage.from("Let me check.", List.of(Fixtures.request("t" + i, "health", "{}"))));
            history.add(ToolExecutionResultMessage.from("t" + i, "health", OUTPUT));
        }
        for (int i = 0; i < 2; i++) {
            history.add(AiMessage.from(Fixtures.request("p" + i, "health", "{}"),
                    Fixtures.request("q" + i, "health", "{}")));
            history.add(ToolExecutionResultMessage.from("p" + i, "health", OUTPUT));
            history.add(ToolExecutionResultMessage.from("q" + i, "health", OUTPUT));
        }
        // the assistant's text could matter, and parallel calls are not simple polling: nothing is collapsed
        assertThat(new DuplicateCollapseStrategy().compact(history, Fixtures.context(10_000))).isEqualTo(history);
    }

    @Test
    void keepsTheConversationAroundRepeatedCalls() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("Is the API up?"));
        poll(history, "p0", "{}", OUTPUT);
        history.add(AiMessage.from("Still down."));
        history.add(UserMessage.from("And now?"));
        poll(history, "p1", "{}", OUTPUT);
        history.add(AiMessage.from("Still down."));

        List<ChatMessage> view = new DuplicateCollapseStrategy().compact(history, Fixtures.context(10_000));
        assertThat(view).contains(history.get(1), history.get(4), history.get(5)).doesNotContain(history.get(2));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void doesNotCollapseWhenTheAnnotationCostsMoreThanItSaves() {
        List<ChatMessage> history = polling("UP", "UP");
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = new DuplicateCollapseStrategy().compact(history, context);
        assertThat(view).isEqualTo(history);
        assertThat(context.notes).isEmpty();
    }
}
