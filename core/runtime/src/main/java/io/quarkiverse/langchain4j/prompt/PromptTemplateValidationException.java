package io.quarkiverse.langchain4j.prompt;

/**
 * Thrown when the variables declared by a prompt template resolved from a registry do not match the variables bound
 * by the AI service method that uses it.
 */
public class PromptTemplateValidationException extends RuntimeException {

    public PromptTemplateValidationException(String message) {
        super(message);
    }
}
