package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

/**
 * Thrown when a prompt template resolved from Apicurio Registry declares required variables that are not provided when
 * rendering it.
 */
public class PromptTemplateValidationException extends RuntimeException {

    public PromptTemplateValidationException(String message) {
        super(message);
    }
}
