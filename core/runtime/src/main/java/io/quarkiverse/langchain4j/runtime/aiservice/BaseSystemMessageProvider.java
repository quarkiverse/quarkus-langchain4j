package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * Common super-type for the system message providers, so a single {@code @RegisterAiService}
 * attribute can accept either a {@link SystemMessageProvider} or a {@link SystemMessageProviderWithContext}.
 * <p>
 * Implement one of the sub-interfaces, not this one directly.
 */
public interface BaseSystemMessageProvider {
}
