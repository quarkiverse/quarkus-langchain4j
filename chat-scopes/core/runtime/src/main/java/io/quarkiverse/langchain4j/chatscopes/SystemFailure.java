package io.quarkiverse.langchain4j.chatscopes;

/**
 * Thrown when a system failure occurs. This results in a failed event being sent back to the client.
 */
public class SystemFailure extends RuntimeException {

    public SystemFailure(String message) {
        super(message);
    }

    public SystemFailure(Throwable cause) {
        super(cause);
    }
}