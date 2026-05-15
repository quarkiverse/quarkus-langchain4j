package io.quarkiverse.langchain4j.runtime.aiservice;

import java.time.Instant;

/**
 * Carries the thinking/reasoning content returned by the model on a
 * non-streaming response. Passed to the
 * {@link io.quarkiverse.langchain4j.OnThinking @OnThinking}-annotated static
 * handler declared on the AI service interface.
 */
public interface ThinkingEmitted {

    /**
     * @return the thinking text returned by the model
     */
    String text();

    /**
     * @return the AI service method whose call produced the thinking
     */
    String methodName();

    /**
     * @return the AI service interface that declared the method
     */
    Class<?> serviceClass();

    /**
     * @return the chat memory id associated with the invocation, or {@code null}
     */
    Object memoryId();

    /**
     * @return timestamp captured when the event was built
     */
    Instant emittedAt();
}
