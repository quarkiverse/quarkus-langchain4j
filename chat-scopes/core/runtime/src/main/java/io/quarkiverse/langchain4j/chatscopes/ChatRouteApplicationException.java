package io.quarkiverse.langchain4j.chatscopes;

/**
 * Usually any exception thrown from a chat route will cause a failed event to be sent back to the client.
 * If a ChatRouteApplicationException is thrown, the runtime will not send a failed event, but will instead
 * send a completed message to the client.
 */
public class ChatRouteApplicationException extends RuntimeException {
    /**
     *
     * @param message
     */
    public ChatRouteApplicationException(String message) {
        super(message);
    }

    public ChatRouteApplicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChatRouteApplicationException(Throwable cause) {
        super(cause);
    }
}
