package io.quarkiverse.langchain4j.watsonx.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ResultReferenceConfig {

    /**
     * The id of the connection asset used to store the extracted results.
     */
    String connection();

    /**
     * The name of the bucket where the output files will be written.
     */
    String bucketName();
}
