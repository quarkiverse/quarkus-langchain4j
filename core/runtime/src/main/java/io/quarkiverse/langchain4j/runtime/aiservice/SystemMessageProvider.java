package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;

/**
 * Provides a way for an AiService to dynamically supply a system message based on the memory ID.
 * This is useful when the system message needs to be determined at runtime, for example based on user context
 * or other dynamic factors.
 * <p>
 * This corresponds to LangChain4j's {@code systemMessageProvider} functionality.
 *
 * @see <a href="https://docs.langchain4j.dev/tutorials/ai-services#system-message-provider">LangChain4j System Message
 *      Provider</a>
 */
public interface SystemMessageProvider {

    /**
     * Provides a system message for the given memory ID.
     *
     * @param memoryId the memory ID for which to provide a system message
     * @return an optional containing the system message, or empty if no system message should be used
     */
    Optional<String> getSystemMessage(Object memoryId);
}
