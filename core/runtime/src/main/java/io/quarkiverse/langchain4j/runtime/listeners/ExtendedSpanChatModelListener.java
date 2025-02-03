package io.quarkiverse.langchain4j.runtime.listeners;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.trace.Span;

/** 
 * extended ChatModelListener adding possibility to add custom attributes to span
 */
public interface ExtendedSpanChatModelListener {
    /**
     * Allowing extendes Span-Attributes {@link SpanChatModelListener} 
     *
     * @param requestContext The request context. It contains the {@link ChatModelRequest} and attributes.
     *                       The attributes can be used to pass data between methods of this listener
     *                       or between multiple listeners.
     * @param currentSpan Span opened by {@link SpanChatModelListener}.
     */
    default void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {

    }

    /**
     * Allowing extendes Span-Attributes {@link SpanChatModelListener} 
     *
     * @param responseContext The response context.
     *                        It contains {@link ChatModelResponse}, corresponding {@link ChatModelRequest} and attributes.
     *                        The attributes can be used to pass data between methods of this listener
     *                        or between multiple listeners.
     * @param currentSpan Span opened by {@link SpanChatModelListener}.
     */
    default void onResponse(ChatModelResponseContext responseContext, Span currentSpan) {

    }

    /**
     * Allowing extendes Span-Attributes {@link SpanChatModelListener} 
     *
     * @param errorContext The error context.
     *                     It contains the error, corresponding {@link ChatModelRequest},
     *                     partial {@link ChatModelResponse} (if available) and attributes.
     *                     The attributes can be used to pass data between methods of this listener
     *                     or between multiple listeners.
     * @param currentSpan Span opened by {@link SpanChatModelListener}.
     */
    default void onError(ChatModelErrorContext errorContext, Span currentSpan) {

    }
    
}
