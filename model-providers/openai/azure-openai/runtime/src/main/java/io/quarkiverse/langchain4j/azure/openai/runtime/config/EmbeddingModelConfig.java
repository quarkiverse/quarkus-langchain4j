package io.quarkiverse.langchain4j.azure.openai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface EmbeddingModelConfig {
    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.resource-name}
     * specifically for embedding models if it is set.
     */
    Optional<String> resourceName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.domain-name}
     * specifically for embedding models if it is set.
     */
    Optional<String> domainName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.deployment-name}
     * specifically for embedding models if it is set.
     */
    Optional<String> deploymentName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.endpoint}
     * specifically for embedding models if it is set.
     */
    Optional<String> endpoint();

    /**
     * The Azure AD token to use for this operation.
     * If present, then the requests towards OpenAI will include this in the Authorization header.
     * Note that this property overrides the functionality of {@code quarkus.langchain4j.azure-openai.embedding-model.api-key}.
     */
    Optional<String> adToken();

    /**
     * The API version to use for this operation. This follows the YYYY-MM-DD format
     */
    Optional<String> apiVersion();

    /**
     * Azure OpenAI API key
     */
    Optional<String> apiKey();

    /**
     * Whether embedding model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether embedding model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
