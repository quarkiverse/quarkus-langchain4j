package io.quarkiverse.langchain4j.bedrock.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;

public interface AwsClientConfig {

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
    Optional<String> credentialsProvider();

    /**
     * The maximum number retries the aws sdk client will attempt.
     */
    Optional<Integer> maxRetries();

    /**
     * Timeout for Bedrock calls
     */
    @ConfigDocDefault("10s")
    Optional<Duration> timeout();

    /**
     * Whether chat model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Whether chat model body in request and response should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logBody();
}
