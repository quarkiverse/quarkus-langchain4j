package io.quarkiverse.langchain4j.chatscopes.internal;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteExceptionHandler;

/**
 * Thrown internally if an exception propagates to chat route runtime.
 * Create a {@link ChatRouteExceptionHandler} for this exception if you want
 * a catch-all for unhandled application exceptions.
 */
public class UnhandledApplicationException extends RuntimeException {
    public UnhandledApplicationException(Throwable cause) {
        super(cause);
    }
}
