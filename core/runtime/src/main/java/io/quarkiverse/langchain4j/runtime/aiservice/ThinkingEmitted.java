package io.quarkiverse.langchain4j.runtime.aiservice;

import java.time.Instant;

/**
 * Carries the thinking/reasoning content returned by the model on a
 * non-streaming response. Passed to the
 * {@link io.quarkiverse.langchain4j.Thinking @Thinking}-annotated static
 * handler declared on the AI service interface.
 *
 * @param text the thinking text returned by the model
 * @param methodName the AI service method whose call produced the thinking
 * @param serviceClass the AI service interface that declared the method
 * @param memoryId the chat memory id associated with the invocation, or {@code null}
 * @param emittedAt timestamp captured when the record was built
 */
public record ThinkingEmitted(String text,
        String methodName,
        Class<?> serviceClass,
        Object memoryId,
        Instant emittedAt) {
}
