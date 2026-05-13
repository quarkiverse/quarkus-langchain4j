package io.quarkiverse.langchain4j.runtime.aiservice;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.chat-memory")
public interface ChatMemoryConfig {

    /**
     * Configures aspects of the {@link MessageWindowChatMemory} which is the default {@link dev.langchain4j.memory.ChatMemory}
     * setup by the extension.
     * This only has effect if {@code quarkus.langchain4j.chat-memory.type} has not been configured (or is configured to
     * {@code memory-window}) and no bean of
     * type {@link ChatMemoryProvider}
     * is present in the application.
     */
    MemoryWindow memoryWindow();

    /**
     * Configures aspects of the {@link TokenWindowChatMemory} which is enabled if the
     * {@code quarkus.langchain4j.chat-memory.type} configuration property
     * is set to {@code token-window} and if no nd no bean of type {@link ChatMemoryProvider} is present in the application.
     */
    TokenWindow tokenWindow();

    /**
     * Whether to delete chat memory from the underlying
     * {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} when the owning AI service bean
     * is destroyed (typically at application shutdown, or at the end of each request for
     * {@link jakarta.enterprise.context.RequestScoped} AI services).
     * <p>
     * When {@code true} (the default), historical behavior is preserved: every known memory id is
     * removed from the store. This is appropriate for the in-memory default store.
     * <p>
     * When {@code false}, the in-memory bookkeeping is still released to avoid leaks, but the
     * persistent store is left untouched, so conversations backed by a custom
     * {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} (e.g., Redis, Couchbase, JDBC)
     * survive pod or application restarts.
     */
    @WithDefault("true")
    boolean clearOnClose();

    @ConfigGroup
    interface MemoryWindow {

        /**
         * The maximum number of messages the configured {@link MessageWindowChatMemory} will hold
         */
        @WithDefault("10")
        int maxMessages();
    }

    @ConfigGroup
    interface TokenWindow {

        /**
         * The maximum number of tokens the configured {@link TokenWindowChatMemory} will hold
         */
        @WithDefault("1000")
        int maxTokens();
    }

}
