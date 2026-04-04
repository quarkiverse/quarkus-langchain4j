package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * Strategy interface that controls when messages are committed to the
 * {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} during an AI service invocation.
 * <p>
 * By default, Quarkus Langchain4j accumulates messages in memory and only persists them
 * to the {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} after the invocation
 * completes successfully. This enables seamless {@code @Retry} support - on failure the
 * uncommitted messages are discarded and the retry starts with a clean slate.
 * <p>
 * When auto-commit is enabled, each message is persisted to the
 * {@code ChatMemoryStore} immediately when it is added. The store always reflects the
 * current state of the conversation, even if the invocation eventually fails.
 * <p>
 * Usage:
 *
 * <pre>
 * {@code @RegisterAiService}(
 *     chatMemoryCommitStrategySupplier = MyCommitStrategySupplier.class
 * )
 * </pre>
 *
 * Where {@code MyCommitStrategySupplier} implements {@code Supplier<ChatMemoryCommitStrategy>}.
 */
public interface ChatMemoryCommitStrategy {

    /**
     * Whether to persist messages to the
     * {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} immediately
     * when they are added, rather than deferring until the invocation succeeds.
     */
    boolean isAutoCommit();

    /**
     * The default strategy: accumulate messages in memory and only persist them
     * after a successful invocation.
     */
    ChatMemoryCommitStrategy DEFAULT = () -> false;
}
