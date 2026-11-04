package io.quarkiverse.langchain4j.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;

class CompactionInvariantsTest {

    private final List<ChatMessage> original = Fixtures.conversation(4);

    @Test
    void acceptsTheOriginal() {
        assertThat(CompactionInvariants.violations(original, original)).isEmpty();
    }

    @Test
    void detectsDroppedSystemMessage() {
        assertThat(CompactionInvariants.violations(original, original.subList(1, original.size())))
                .anyMatch(v -> v.contains("system message dropped"));
    }

    @Test
    void detectsToolResultWithoutRequest() {
        List<ChatMessage> broken = new ArrayList<>(original);
        broken.remove(2); // the AiMessage requesting call-0
        assertThat(CompactionInvariants.violations(original, broken))
                .anyMatch(v -> v.contains("no matching request"));
    }

    @Test
    void detectsRequestWithoutResult() {
        List<ChatMessage> broken = new ArrayList<>(original);
        broken.remove(3); // the result of call-0
        assertThat(CompactionInvariants.violations(original, broken)).anyMatch(v -> v.contains("have no result"));
    }

    @Test
    void detectsReordering() {
        List<ChatMessage> broken = new ArrayList<>(original);
        ChatMessage last = broken.remove(broken.size() - 1);
        broken.add(1, last);
        assertThat(CompactionInvariants.violations(original, broken)).anyMatch(v -> v.contains("out of order"));
    }

    @Test
    void ignoresDerivedMessages() {
        List<ChatMessage> view = new ArrayList<>();
        view.add(Fixtures.SYSTEM);
        view.add(CompactionMessages.summary("earlier"));
        view.addAll(original.subList(original.size() - 3, original.size()));
        assertThat(CompactionInvariants.violations(original, view)).isEmpty();
    }
}
