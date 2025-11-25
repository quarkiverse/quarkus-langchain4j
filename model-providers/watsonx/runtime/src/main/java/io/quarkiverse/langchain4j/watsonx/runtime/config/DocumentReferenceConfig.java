package io.quarkiverse.langchain4j.watsonx.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface DocumentReferenceConfig {

    /**
     * The id of the connection asset that contains the credentials required to access the data.
     */
    String connection();

    /**
     * The name of the bucket containing the input document.
     */
    String bucketName();
}
