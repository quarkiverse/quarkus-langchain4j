package io.quarkiverse.langchain4j.runtime.tool;

import io.opentelemetry.api.trace.Span;

/**
 * Defines a contributor interface for adding behavior to the lifecycle of tool execution spans.
 *
 * The ToolSpanContributor interface provides methods for customizing the interaction with spans
 * during a tool's execution process. Implementations can override default methods to introduce
 * logic for use cases such as instrumentation, logging, or tracing during request and response events.
 */
public interface ToolSpanContributor {
    /**
     * Allows for custom data to be added to the span prior to tool execution
     *
     * @param context the {@code ToolExecutionRequestContext} containing the details of the tool execution request,
     *        including the request and invocation context
     * @param span the {@code Span} representing the tracing span associated with the tool execution request
     */
    default void onRequest(ToolExecutionRequestContext context, Span span) {

    }

    /**
     * Allows for custom data to be added to the span after tool execution
     *
     * @param context the {@code ToolExecutionResponseContext} containing details of the
     *        tool execution response, including the request context and the result
     *        of the execution
     * @param span the {@code Span} representing the tracing span associated with the
     *        tool execution, which can be augmented or annotated
     */
    default void onResponse(ToolExecutionResponseContext context, Span span) {

    }

    /**
     * Allows for custom data to be added to the span if an error occurs during tool execution
     *
     * @param context the {@code ToolExecutionErrorContext} containing details about the error
     *        encountered during the tool's execution, including the request context
     *        and associated error.
     * @param span the {@code Span} representing the tracing span associated with the tool's
     *        execution, which can be augmented or annotated to capture error-specific details.
     */
    default void onError(ToolExecutionErrorContext context, Span span) {

    }
}
