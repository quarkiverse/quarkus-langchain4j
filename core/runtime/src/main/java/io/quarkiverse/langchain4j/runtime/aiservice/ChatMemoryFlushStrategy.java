package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * <pre>
 * Controls when messages are flushed (persisted) to the ChatMemoryStore
 * during an AI service invocation.
 *
 * DEFERRED (default):
 *   Messages are accumulated in memory and only persisted to the
 *   ChatMemoryStore after the invocation completes successfully.
 *   This approach that enables seamless @Retry
 *   support - on failure the uncommitted messages are discarded and
 *   the retry starts with a clean slate.
 *
 * IMMEDIATE:
 *   Each message is persisted to the ChatMemoryStore immediately
 *   when it is added (write-through). The store always reflects
 *   the current state of the conversation, even if the invocation
 *   eventually fails.
 *
 * Usage:
 *
 *   &#64;RegisterAiService(
 *       chatMemoryFlushStrategySupplier = MyFlushStrategySupplier.class
 *   )
 *
 * Where MyFlushStrategySupplier implements Supplier&lt;ChatMemoryFlushStrategy&gt;.
 * </pre>
 */
public enum ChatMemoryFlushStrategy {

    /**
     * Writes messages to the ChatMemoryStore immediately
     * when they are added.
     */
    IMMEDIATE,

    /**
     * Buffer messages in memory and writes them only after the LLM invocation
     * succeeds.
     */
    DEFERRED
}
