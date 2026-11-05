package io.quarkiverse.langchain4j.compaction;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.data.message.ChatMessage;
import io.smallrye.common.annotation.Experimental;

/**
 * Everything a {@link CompactionStrategy} needs besides the messages themselves.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface CompactionContext {

    /**
     * @return the memory id of the conversation being compacted
     */
    Object memoryId();

    /**
     * @return the token budget of the target model
     */
    TokenBudget budget();

    /**
     * @return the token counter used to measure messages
     */
    TokenCounter tokenCounter();

    /**
     * @return the time the message was added to the conversation, if known
     */
    Optional<Instant> timestamp(ChatMessage message);

    /**
     * @return the current time, as seen by the application (overridable in tests)
     */
    Instant now();

    /**
     * Per-conversation state owned by a strategy, kept between compactions of the same conversation. Strategies use it
     * to cache expensive results such as summaries or embeddings.
     *
     * @param owner the strategy identifier
     * @return a mutable map, never {@code null}
     */
    Map<String, Object> state(String owner);

    /**
     * Records a human-readable note in the compaction report (what was pruned, summarized, dropped...).
     */
    void note(String owner, String note);

    /**
     * Records that a step could not do its job (for example the summarizer failed) and kept the messages it should have
     * compacted. The report is marked {@link CompactionReport#degraded() degraded}, and the view is cut down with
     * {@code token-window} if it does not fit the budget.
     */
    default void degraded(String owner, String reason) {
        note(owner, reason);
    }

    /**
     * @return the number of tokens of the given messages, as measured by {@link #tokenCounter()}
     */
    default int tokens(java.util.List<ChatMessage> messages) {
        return tokenCounter().count(messages);
    }

    /**
     * @return {@code true} if the given messages fit within the compaction target
     */
    default boolean fits(java.util.List<ChatMessage> messages) {
        return tokens(messages) <= budget().target();
    }
}
