package io.quarkiverse.langchain4j.runtime.aiservice;

import java.time.Instant;

/**
 * Default {@link ThinkingEmitted} implementation built by the AI service
 * runtime when a non-streaming response carries thinking content.
 */
record DefaultThinkingEmitted(String text,
        String methodName,
        Class<?> serviceClass,
        Object memoryId,
        Instant emittedAt) implements ThinkingEmitted {
}
