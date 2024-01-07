package io.quarkiverse.langchain4j.opensearch;

public class OpenSearchRequestFailedException extends RuntimeException {

    public OpenSearchRequestFailedException() {
        super();
    }

    public OpenSearchRequestFailedException(String message) {
        super(message);
    }

    public OpenSearchRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}