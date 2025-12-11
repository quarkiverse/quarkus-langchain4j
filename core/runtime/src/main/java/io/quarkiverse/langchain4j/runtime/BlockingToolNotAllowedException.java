package io.quarkiverse.langchain4j.runtime;

public class BlockingToolNotAllowedException extends RuntimeException implements PreventsErrorHandlerExecution {

    public BlockingToolNotAllowedException(String message) {
        super(message);
    }
}
