package io.quarkiverse.langchain4j.compaction;

public class CompactionException extends RuntimeException {

    public CompactionException(String message) {
        super(message);
    }

    public CompactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
