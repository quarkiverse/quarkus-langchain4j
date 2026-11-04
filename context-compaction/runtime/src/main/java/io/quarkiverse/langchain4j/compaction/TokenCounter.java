package io.quarkiverse.langchain4j.compaction;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Measures messages in tokens.
 * <p>
 * The extension provides a default implementation that delegates to a LangChain4j
 * {@link dev.langchain4j.model.TokenCountEstimator} when one is available and falls back to a character-based
 * heuristic otherwise. Applications can replace it by declaring their own bean.
 */
public interface TokenCounter {

    int count(ChatMessage message);

    default int count(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += count(message);
        }
        return total;
    }
}
