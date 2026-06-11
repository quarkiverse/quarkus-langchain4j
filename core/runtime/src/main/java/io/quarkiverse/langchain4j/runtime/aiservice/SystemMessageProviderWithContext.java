package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;

import dev.langchain4j.invocation.InvocationContext;

/**
 * Provides a way for an AiService to dynamically supply a system message based on the invocation context,
 * which exposes the model in use (provider and default request parameters such as the model name).
 * This allows the system message to vary by model.
 * <p>
 * If the system message only depends on the memory ID, implement {@link SystemMessageProvider} instead.
 * <p>
 * This corresponds to LangChain4j's {@code systemMessageProviderWithContext} functionality.
 */
public interface SystemMessageProviderWithContext extends BaseSystemMessageProvider {

    /**
     * Provides a system message based on the invocation context.
     *
     * @param context the invocation context
     * @return an optional containing the system message, or empty if no system message should be used
     */
    Optional<String> getSystemMessage(InvocationContext context);
}
