package io.quarkiverse.langchain4j.audit;

/**
 * Allow applications to audit parts of the interactions with the LLM that interest them
 * <p>
 * When using {@link io.quarkiverse.langchain4j.RegisterAiService} if the application provides an implementation
 * of {@link AuditService} that is a CDI bean, it will be used by default.
 */
public interface AuditService {

    /**
     * Invoked when an AiService method is invoked and before any interaction with the LLM is performed.
     */
    Audit create(Audit.CreateInfo createInfo);

    /**
     * Invoked just before the AiService method returns its result - or throws an exception.
     * The {@param audit} parameter is meant to be built up by implementing its callbacks.
     */
    void complete(Audit audit);
}
