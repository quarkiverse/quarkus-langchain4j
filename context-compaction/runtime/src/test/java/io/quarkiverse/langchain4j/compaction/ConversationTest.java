package io.quarkiverse.langchain4j.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

class ConversationTest {

    @Test
    void groupsToolCallsWithTheirResults() {
        Conversation conversation = Conversation.parse(Fixtures.conversation(3));
        assertThat(conversation.system()).containsExactly(Fixtures.SYSTEM);
        assertThat(conversation.segments()).extracting(Conversation.Segment::kind).containsExactly(
                Conversation.Kind.USER, Conversation.Kind.TOOL_EXCHANGE, Conversation.Kind.AI,
                Conversation.Kind.USER, Conversation.Kind.AI,
                Conversation.Kind.USER, Conversation.Kind.TOOL_EXCHANGE, Conversation.Kind.AI);
        assertThat(conversation.segments().get(1).messages()).hasSize(2);
        assertThat(conversation.turns()).hasSize(3);
    }

    @Test
    void reassemblesToTheOriginal() {
        List<ChatMessage> messages = Fixtures.conversation(5);
        Conversation conversation = Conversation.parse(messages);
        assertThat(conversation.assemble(conversation.segments())).isEqualTo(messages);
        assertThat(conversation.assembleTurns(conversation.turns())).isEqualTo(messages);
    }

    @Test
    void flagsOrphanToolResultsAndDropsThemOnAssembly() {
        List<ChatMessage> messages = List.of(Fixtures.SYSTEM,
                ToolExecutionResultMessage.from("lost", "lookupOrder", "orphan"),
                UserMessage.from("hello"));
        Conversation conversation = Conversation.parse(messages);
        assertThat(conversation.segments().get(0).kind()).isEqualTo(Conversation.Kind.ORPHAN_TOOL_RESULT);
        assertThat(conversation.assemble(conversation.segments())).containsExactly(Fixtures.SYSTEM,
                UserMessage.from("hello"));
    }

    @Test
    void recognizesSummaries() {
        Conversation conversation = Conversation.parse(List.of(CompactionMessages.summary("s"), UserMessage.from("q")));
        assertThat(conversation.segments()).extracting(Conversation.Segment::kind)
                .containsExactly(Conversation.Kind.SUMMARY, Conversation.Kind.USER);
        assertThat(conversation.turns().get(0).isSummary()).isTrue();
    }
}
