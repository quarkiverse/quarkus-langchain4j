package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface TextClassificationConfig {

    /**
     * Base URL of the Cloud Object Storage API.
     */
    String cosUrl();

    /**
     * The reference to the document that needs to be processed.
     * <p>
     * This reference includes the COS connection asset ID and the bucket where the document resides. It is required to locate
     * and access the input
     * document for extraction.
     */
    DocumentReferenceConfig documentReference();

    /**
     * Whether text extraction requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether text extraction responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Whether the watsonx.ai client should log requests as cURL commands.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequestsCurl();
}
