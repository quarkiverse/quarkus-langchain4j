package io.quarkiverse.langchain4j.bedrock.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;

public interface AwsClientConfig {
    /**
     * Whether requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Whether body in request and response should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logBody();

    /**
     * Aws sdk related configurations
     */
    AwsConfig aws();
}
