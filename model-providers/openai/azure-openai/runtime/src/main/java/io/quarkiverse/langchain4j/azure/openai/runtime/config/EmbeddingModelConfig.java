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
