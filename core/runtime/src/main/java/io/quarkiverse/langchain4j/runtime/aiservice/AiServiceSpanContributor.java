package io.quarkiverse.langchain4j.runtime.aiservice;

import io.opentelemetry.api.trace.Span;

/**
 * Adds custom data to the span of an AI service invocation ({@code langchain4j.aiservices.*}).
 * <p>
 * Implementations are CDI beans. Every implementation is notified when the invocation starts, and then when it either
 * completes or fails. For a streaming method, the invocation completes or fails when the returned stream does, not when
 * the method returns. An exception thrown by a contributor is recorded on the span and logged, and never fails the
 * invocation.
 */
public interface AiServiceSpanContributor {

    /**
     * Called once the span has started, before the AI service does any work.
     *
     * @param context the invocation, including its method arguments
     * @param span the span of the invocation
     */
    default void onStart(AiServiceSpanStartContext context, Span span) {

    }

    /**
     * Called when the invocation completes, just before the span ends. A canceled stream also completes, without a
     * result.
     *
     * @param context the invocation and its result
     * @param span the span of the invocation
     */
    default void onComplete(AiServiceSpanCompleteContext context, Span span) {

    }

    /**
     * Called when the invocation fails, just before the span ends.
     *
     * @param context the invocation and its failure
     * @param span the span of the invocation
     */
    default void onError(AiServiceSpanErrorContext context, Span span) {

    }
}
