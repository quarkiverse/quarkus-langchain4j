package io.quarkiverse.langchain4j.chatscopes;

/**
 * Usually any exception thrown from a chat route will cause a failed event to be sent back to the client.
 * If this exception is thrown, the runtime will not send a failed event, but will instead
 * complete the client request.
 */
public class HandledApplicationException extends RuntimeException {
    public HandledApplicationException(String message) {
        super(message);
    }

    public HandledApplicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public HandledApplicationException(Throwable cause) {
        super(cause);
    }
}
