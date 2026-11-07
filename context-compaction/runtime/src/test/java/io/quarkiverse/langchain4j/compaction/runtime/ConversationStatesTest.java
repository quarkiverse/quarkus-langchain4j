package io.quarkiverse.langchain4j.compaction.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConversationStatesTest {

    private final CompactingChatMemoryTest.MutableClock clock = new CompactingChatMemoryTest.MutableClock(
            Instant.parse("2026-11-05T12:00:00Z"));
    private final List<Object> evicted = new ArrayList<>();

    @Test
    void evictsTheLeastRecentlyUsedConversations() {
        ConversationStates states = new ConversationStates(2, Duration.ofHours(1), clock, evicted::add);
        states.run("a", s -> s.lastView = List.of());
        states.run("b", s -> {
        });
        states.run("a", s -> {
        });
        states.run("c", s -> {
        });

        assertThat(states.size()).isEqualTo(2);
        assertThat(evicted).containsExactly("b");
        // the state of a was kept
        List<?> view = states.with("a", s -> s.lastView);
        assertThat(view).isNotNull();
    }

    @Test
    void evictsIdleConversations() {
        ConversationStates states = new ConversationStates(100, Duration.ofMinutes(30), clock, evicted::add);
        states.run("a", s -> {
        });
        clock.advance(Duration.ofMinutes(20));
        states.run("b", s -> {
        });
        clock.advance(Duration.ofMinutes(20));
        states.run("c", s -> {
        });

        assertThat(evicted).containsExactly("a");
        assertThat(states.size()).isEqualTo(2);
    }

    @Test
    void neverEvictsAConversationInUse() {
        ConversationStates states = new ConversationStates(1, Duration.ofMinutes(30), clock, evicted::add);
        states.run("a", a -> {
            a.lastView = List.of();
            clock.advance(Duration.ofHours(1));
            // another conversation is used while a is being compacted
            states.run("b", b -> {
            });
            assertThat(evicted).doesNotContain("a");
        });
        List<?> view = states.with("a", s -> s.lastView);
        assertThat(view).isNotNull();
    }
}
