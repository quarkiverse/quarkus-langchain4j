package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.net.URL;
import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface IAMConfig {

    /**
     * IAM base URL
     */
    @WithDefault("https://iam.cloud.ibm.com")
    URL baseUrl();

    /**
     * Timeout for IAM API calls
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * IAM grant type
     */
    @WithDefault("urn:ibm:params:oauth:grant-type:apikey")
    String grantType();
}
