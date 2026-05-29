package io.quarkiverse.langchain4j.chatscopes.internal;

import io.quarkiverse.langchain4j.chatscopes.SystemFailure;

/**
 * A system failure that occurred during the invocation of a chat route method.
 * This is different than {@link UnhandledApplicationException} in that the cause
 * exception was thrown by the chat route runtime.
 */
public class InvocationFailure extends SystemFailure {
    public InvocationFailure(Throwable cause) {
        super(cause);
    }
}
