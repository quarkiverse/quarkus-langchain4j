package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.net.URL;
import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface IAMConfig {

    /**
     * Base URL of the IAM Authentication API.
     */
    @WithDefault("https://iam.cloud.ibm.com")
    URL baseUrl();

    /**
     * Timeout for IAM authentication calls.
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * Grant type for the IAM Authentication API.
     */
    @WithDefault("urn:ibm:params:oauth:grant-type:apikey")
    String grantType();
}
