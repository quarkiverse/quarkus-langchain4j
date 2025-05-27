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
     * <p>
     * This reference includes the COS connection asset ID and the bucket where the document resides. It is required to locate
     * and access the input
     * document for extraction.
     */
    DocumentReference documentReference();

    /**
     * The reference where the extracted text results will be stored.
     * <p>
     * This reference defines the COS connection asset ID and the target bucket for storing the output of the text extraction
     * process.
     */
    ResultsReference resultsReference();

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

    /**
     * Configuration for the document reference location in COS.
     */
    @ConfigGroup
    interface DocumentReference {

        /**
         * The ID of the connection asset that contains the credentials required to access the data.
         */
        String connection();

        /**
         * The name of the bucket containing the input document.
         */
        String bucketName();
    }

    /**
     * Configuration for the results reference location in COS.
     */
    @ConfigGroup
    interface ResultsReference {

        /**
         * The ID of the connection asset used to store the extracted results.
         */
        String connection();

        /**
         * The name of the bucket where the output files will be written.
         */
        String bucketName();
    }
}
