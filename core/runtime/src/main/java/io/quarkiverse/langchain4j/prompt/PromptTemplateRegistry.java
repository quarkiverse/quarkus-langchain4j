package io.quarkiverse.langchain4j.prompt;

/**
 * Resolves prompt templates referenced by {@link io.quarkiverse.langchain4j.SystemMessageFromRegistry} and
 * {@link io.quarkiverse.langchain4j.UserMessageFromRegistry} from an external registry.
 * <p>
 * Implementations are provided as CDI beans by registry extensions (for example
 * {@code quarkus-langchain4j-prompt-apicurio-registry}) and are expected to cache resolved templates.
 */
public interface PromptTemplateRegistry {

    /**
     * Resolves the given reference.
     *
     * @throws PromptTemplateResolutionException if the template cannot be fetched or is not a valid prompt template
     */
    ResolvedPromptTemplate resolve(PromptTemplateReference reference);
}
