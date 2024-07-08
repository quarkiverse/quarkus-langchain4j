package io.quarkiverse.langchain4j.runtime.cache;

import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Represents the cache of a AI. It can be used to reduces response time for similar queries.
 */
public interface AiCache {

    /**
     * The ID of the {@link AiCache}.
     *
     * @return The ID of the {@link AiCache}.
     */
    Object id();

    /**
     * Cache a new message.
     *
     * @param systemMessage {@link SystemMessage} value to add to the cache.
     * @param userMessage {@link UserMessage} value to add to the cache.
     * @param aiResponse {@link AiMessage} value to add to the cache.
     */
    void add(SystemMessage systemMessage, UserMessage userMessage, AiMessage aiResponse);

    /**
     * Check if there is a response in the cache that is semantically close to the cached items.
     *
     * @param systemMessage {@link SystemMessage} value to find in the cache.
     * @param userMessage {@link UserMessage} value to find in the cache.
     * @return
     */
    Optional<AiMessage> search(SystemMessage systemMessage, UserMessage userMessage);

    /**
     * Clears the cache.
     */
    void clear();
}
