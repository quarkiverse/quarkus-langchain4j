package io.quarkiverse.langchain4j.test.guardrails;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}