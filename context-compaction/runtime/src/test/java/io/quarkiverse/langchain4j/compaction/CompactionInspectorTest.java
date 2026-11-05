package io.quarkiverse.langchain4j.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

class CompactionInspectorTest {

    @Test
    void keepsTheSnapshotsOfTheMostRecentlyActiveConversations() {
        CompactionInspector inspector = new CompactionInspector();
        List<ChatMessage> messages = List.of(UserMessage.from("hello"));
        inspector.record("first", messages, messages, null);
        for (int i = 0; i < CompactionInspector.MAX_SNAPSHOTS; i++) {
            inspector.record("conversation-" + i, messages, messages, null);
            // first stays active
            inspector.snapshot("first");
        }
        inspector.record("last", messages, messages, null);

        assertThat(inspector.snapshot("first")).isPresent();
        assertThat(inspector.snapshot("conversation-0")).isEmpty();
        assertThat(inspector.snapshot("last")).isPresent();
    }
}
