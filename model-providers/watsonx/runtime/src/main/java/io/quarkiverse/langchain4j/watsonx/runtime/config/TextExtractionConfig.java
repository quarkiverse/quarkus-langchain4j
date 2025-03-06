package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface TextExtractionConfig {

    /**
     * Base URL of the Cloud Object Storage API.
     */
    String baseUrl();

    /**
     * The reference to the document that needs to be processed.
     */
    Reference documentReference();

    /**
     * The reference where the extracted text results will be stored.
     */
    Reference resultsReference();

    /**
     * Whether the Cloud Object Storage client should log requests.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether the Cloud Object Storage client should log responses.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    @ConfigGroup
    interface Reference {

        /**
         * The id of the connection asset that contains the credentials required to access the data.
         */
        String connection();

        /**
         * The default bucket for uploading/extracting documents.
         */
        String bucketName();
    }
}
