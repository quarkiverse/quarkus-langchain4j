package io.quarkiverse.langchain4j.prompt;

/**
 * Thrown when a prompt template cannot be resolved from a registry.
 */
public class PromptTemplateResolutionException extends RuntimeException {

    public PromptTemplateResolutionException(String message) {
        super(message);
    }

    public PromptTemplateResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
