package io.quarkiverse.langchain4j.testing.evaluation;

/**
 * Exception thrown when sample loading fails.
 * <p>
 * This exception is thrown by {@link SampleLoader} implementations when
 * they encounter errors loading samples from a source.
 * </p>
 */
public class SampleLoadException extends RuntimeException {

    /**
     * Create a new SampleLoadException with a message.
     *
     * @param message the error message
     */
    public SampleLoadException(String message) {
        super(message);
    }

    /**
     * Create a new SampleLoadException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public SampleLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new SampleLoadException with a cause.
     *
     * @param cause the underlying cause
     */
    public SampleLoadException(Throwable cause) {
        super(cause);
    }
}
