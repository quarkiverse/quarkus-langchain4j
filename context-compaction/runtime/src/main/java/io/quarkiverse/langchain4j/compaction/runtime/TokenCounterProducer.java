package io.quarkiverse.langchain4j.compaction.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
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
     * Roughly 4 characters per token for English text, plus a fixed overhead per message for role and framing.
     */
    public static class HeuristicTokenCounter implements TokenCounter {

        static final int MESSAGE_OVERHEAD = 4;

        @Override
        public int count(ChatMessage message) {
            // the text of an assistant message includes its tool calls (name and arguments)
            int chars = CompactionMessages.text(message).length();
            return MESSAGE_OVERHEAD + (chars + 3) / 4;
        }
    }
}
