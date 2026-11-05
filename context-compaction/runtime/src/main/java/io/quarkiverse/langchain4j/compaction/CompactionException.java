package io.quarkiverse.langchain4j.compaction;

import io.smallrye.common.annotation.Experimental;

@Experimental("This feature is experimental and the API is subject to change")
public class CompactionException extends RuntimeException {

    public CompactionException(String message) {
        super(message);
    }

    public CompactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
