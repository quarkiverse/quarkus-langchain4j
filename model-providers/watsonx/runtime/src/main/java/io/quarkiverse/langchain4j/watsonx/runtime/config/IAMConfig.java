package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface IAMConfig {

    /**
     * Base URL of the IAM Authentication API.
     */
    Optional<URI> baseUrl();

    /**
     * Timeout for IAM authentication calls.
     */
    @ConfigDocDefault("10s")
    @WithDefault("${quarkus.langchain4j.timeout}")
    Optional<Duration> timeout();

    /**
     * Grant type for the IAM Authentication API.
     */
    Optional<String> grantType();
}
