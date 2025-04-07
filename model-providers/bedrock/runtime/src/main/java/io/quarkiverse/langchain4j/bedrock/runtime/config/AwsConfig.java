package io.quarkiverse.langchain4j.bedrock.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;

public interface AwsConfig {

    /**
     * Region used by the bedrock runtime api. See
     * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html">Regions Supported</a>.
     */
    @ConfigDocDefault("default aws region provider chain")
    Optional<String> region();

    /**
     * Override the endpoint used by the bedrock client
     */
    Optional<String> endpointOverride();

    /**
     * Specify a custom credentials provider to use for the bedrock client. Identified by bean name.
     */
    @ConfigDocDefault("default aws credentials provider chain")
    Optional<String> credentialsProvider();

    /**
     * The maximum number retries the aws sdk client will attempt.
     */
    @ConfigDocDefault("3")
    Optional<Integer> maxRetries();

    /**
     * Configure the amount of time to allow the client to complete the execution of an API call.
     * This timeout covers the entire client execution except for marshalling.
     * This includes request handler execution, all HTTP requests including retries, unmarshalling, etc.
     * This value should always be positive, if present.
     */
    @ConfigDocDefault("13s")
    Optional<Duration> apiCallTimeout();
}
