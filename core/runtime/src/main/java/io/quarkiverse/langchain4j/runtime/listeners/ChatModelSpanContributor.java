package io.quarkiverse.langchain4j.runtime.listeners;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.trace.Span;

/**
 * Contributes custom attributes, events or other data to the spans created by {@link SpanChatModelListener}.
 */
public interface ChatModelSpanContributor {
    /**
     * Allows for custom data to be added to the span.
     *
     * @param requestContext The request context. It contains the {@link ChatModelRequest} and attributes.
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
     *        It contains {@link ChatModelResponse}, corresponding {@link ChatModelRequest} and attributes.
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
     *        It contains the error, corresponding {@link ChatModelRequest},
     *        partial {@link ChatModelResponse} (if available) and attributes.
     *        The attributes can be used to pass data between methods of this listener
     *        or between multiple listeners.
     * @param currentSpan Span opened by {@link SpanChatModelListener}.
     */
    default void onError(ChatModelErrorContext errorContext, Span currentSpan) {
    }

}
