package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Map;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.model.TokenCountEstimator;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.TokenCounter;
import io.quarkus.arc.DefaultBean;

/**
 * Produces the default {@link TokenCounter}: a LangChain4j {@link TokenCountEstimator} when the application has one,
 * a character-based heuristic otherwise.
 */
public class TokenCounterProducer {

    @Produces
    @Singleton
    @DefaultBean
    TokenCounter tokenCounter(Instance<TokenCountEstimator> estimators) {
        if (estimators.isResolvable()) {
            TokenCountEstimator estimator = estimators.get();
            return estimator::estimateTokenCountInMessage;
        }
        return new HeuristicTokenCounter();
    }

    /**
     * Roughly 4 characters per token for English text, plus a fixed overhead per message for role and framing, and a
     * fixed cost per image, audio, video or PDF content (their data is never counted as text).
     */
    public static class HeuristicTokenCounter implements TokenCounter {

        static final int MESSAGE_OVERHEAD = 4;

        /**
         * Estimated tokens of a non-text content. Providers bill media very differently (image size and detail level,
         * audio duration, PDF pages), so these are orders of magnitude, on the high side for a typical image.
         */
        static final Map<ContentType, Integer> MEDIA_TOKENS = Map.of(
                ContentType.IMAGE, 1_000,
                ContentType.AUDIO, 1_000,
                ContentType.VIDEO, 4_000,
                ContentType.PDF, 2_000);

        @Override
        public int count(ChatMessage message) {
            // the text of an assistant message includes its tool calls (name and arguments)
            int chars = CompactionMessages.text(message).length();
            int media = 0;
            for (Content content : CompactionMessages.media(message)) {
                media += MEDIA_TOKENS.getOrDefault(content.type(), 1_000);
            }
            return MESSAGE_OVERHEAD + (chars + 3) / 4 + media;
        }
    }
}
