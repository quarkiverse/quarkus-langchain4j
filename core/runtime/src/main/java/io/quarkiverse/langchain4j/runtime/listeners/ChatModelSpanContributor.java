package io.quarkiverse.langchain4j.runtime.listeners;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.trace.Span;

/**
 * Contributes custom attributes, events or other data to the spans created by {@link SpanChatModelListener}.
 */
public interface ChatModelSpanContributor {
    /**
     * Allows for custom data to be added to the span.
     *
     * @param requestContext The request context. It contains the {@link ChatRequest} and attributes.
     *        The attributes can be used to pass data between methods of this listener
     *        or between multiple listeners.
     * @param currentSpan Span opened by {@link SpanChatModelListener}.
     */
    default void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {
    }

    /**
     * Allows for custom data to be added to the span.
     *
     * @param responseContext The response context.
     *        It contains {@link ChatResponse}, corresponding {@link ChatRequest} and attributes.
     *        The attributes can be used to pass data between methods of this listener
     *        or between multiple listeners.
     * @param currentSpan Span opened by {@link SpanChatModelListener}.
     */
    default void onResponse(ChatModelResponseContext responseContext, Span currentSpan) {
    }

    /**
     * Allows for custom data to be added to the span.
     *
     * @param errorContext The error context.
     *        It contains the error, corresponding {@link ChatRequest},
     *        partial {@link ChatResponse} (if available) and attributes.
     *        The attributes can be used to pass data between methods of this listener
     *        or between multiple listeners.
     * @param currentSpan Span opened by {@link SpanChatModelListener}.
     */
    default void onError(ChatModelErrorContext errorContext, Span currentSpan) {
    }

}
