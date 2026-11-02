package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

/**
 * Thrown when a prompt template cannot be resolved from Apicurio Registry.
 */
public class PromptTemplateResolutionException extends RuntimeException {

    public PromptTemplateResolutionException(String message) {
        super(message);
    }

    public PromptTemplateResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
