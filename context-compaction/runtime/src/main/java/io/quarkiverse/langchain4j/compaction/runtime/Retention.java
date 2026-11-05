package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Limits on the stored history: the oldest turns are deleted when the history is longer, or older, than allowed.
 * <p>
 * Turns are deleted whole (a turn starts with a {@link UserMessage}), so a tool result is never separated from its
 * request. The system message and the last turn are never deleted, and a turn whose age is unknown stops the deletion
 * by age.
 *
 * @param maxMessages the maximum number of stored messages, the system message excluded
 * @param maxAge the maximum age of a stored turn
 */
public record Retention(OptionalInt maxMessages, Optional<Duration> maxAge) {

    /**
     * Keeps everything.
     */
    public static final Retention NONE = new Retention(OptionalInt.empty(), Optional.empty());

    /**
     * When the history exceeds {@link #maxMessages()}, it is trimmed down to this ratio of the limit, so the store is
     * not rewritten on every message once the limit is reached.
     */
    static final double TRIM_RATIO = 0.8;

    public Retention {
        if (maxMessages.isPresent() && maxMessages.getAsInt() <= 0) {
            throw new IllegalArgumentException("retention.max-messages must be positive");
        }
        if (maxAge.isPresent() && (maxAge.get().isNegative() || maxAge.get().isZero())) {
            throw new IllegalArgumentException("retention.max-age must be positive");
        }
    }

    public boolean enabled() {
        return maxMessages.isPresent() || maxAge.isPresent();
    }

    /**
     * @param history the history to store
     * @param timestamps the timestamps of the messages of {@code history} (same size), {@code null} when unknown
     * @param now the current time
     * @return the number of messages to delete at the beginning of the history, system message excluded: the messages to
     *         delete are the first {@code n} messages that are not system messages
     */
    int toDelete(List<ChatMessage> history, List<Instant> timestamps, Instant now) {
        if (!enabled()) {
            return 0;
        }
        // positions, in the history, of the messages that are not system messages
        int[] positions = history.stream().mapToInt(m -> m instanceof SystemMessage ? 0 : 1).toArray();
        int count = 0;
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == 1) {
                positions[count++] = i;
            }
        }
        // the possible cuts: the start of each turn but the first, the last turn is always kept
        int lastTurn = 0;
        for (int k = count - 1; k > 0; k--) {
            if (history.get(positions[k]) instanceof UserMessage) {
                lastTurn = k;
                break;
            }
        }
        if (lastTurn == 0) {
            return 0;
        }

        int cut = 0;
        if (maxMessages.isPresent() && count > maxMessages.getAsInt()) {
            int target = Math.max(1, (int) (maxMessages.getAsInt() * TRIM_RATIO));
            cut = lastTurn;
            for (int k = 1; k < lastTurn; k++) {
                if (history.get(positions[k]) instanceof UserMessage && count - k <= target) {
                    cut = k;
                    break;
                }
            }
        }
        if (maxAge.isPresent()) {
            Instant expiry = now.minus(maxAge.get());
            int ageCut = 0;
            for (int k = 0; k < lastTurn; k++) {
                Instant timestamp = timestamps.get(positions[k]);
                if (timestamp == null || !timestamp.isBefore(expiry)) {
                    break;
                }
                if (history.get(positions[k + 1]) instanceof UserMessage) {
                    // every message of the turn ending at k is expired
                    ageCut = k + 1;
                }
            }
            cut = Math.max(cut, ageCut);
        }
        return cut;
    }
}
