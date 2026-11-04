package io.quarkiverse.langchain4j.compaction;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;

/**
 * A context compaction strategy.
 * <p>
 * A strategy receives the full, chronologically ordered conversation and returns the view that is sent to the
 * model. It must never modify the input conversation, and the returned view must respect the invariants checked by
 * {@link CompactionInvariants}: system messages are kept, tool calls stay paired with their results, and the
 * chronological order of the original messages is preserved.
 * <p>
 * Strategies are CDI beans qualified with {@link io.smallrye.common.annotation.Identifier}. The identifier is the
 * name used in configuration ({@code quarkus.langchain4j.compaction.strategy}) and in pipelines. Applications contribute
 * their own strategy by declaring a bean implementing this interface.
 */
public interface CompactionStrategy {

    /**
     * Compacts the given conversation.
     *
     * @param conversation the parsed conversation history
     * @param context the compaction context (budget, token counter, per-conversation state)
     * @return the compacted view, never {@code null}
     */
    List<ChatMessage> compact(Conversation conversation, CompactionContext context);

    /**
     * Convenience overload that parses messages into a {@link Conversation} first.
     */
    default List<ChatMessage> compact(List<ChatMessage> messages, CompactionContext context) {
        return compact(Conversation.parse(messages), context);
    }

    /**
     * @return a one-line description shown in the Dev UI and in reports
     */
    default String description() {
        return getClass().getSimpleName();
    }
}
