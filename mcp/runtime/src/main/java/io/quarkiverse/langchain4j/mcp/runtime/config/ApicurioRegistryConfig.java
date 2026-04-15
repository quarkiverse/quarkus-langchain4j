package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ApicurioRegistryConfig {

    /**
     * The base URL of the Apicurio Registry instance
     * (e.g. http://localhost:8080/apis/registry/v3).
     */
    String url();

    /**
     * Optional authentication token for the registry.
     */
    Optional<String> authToken();

    /**
     * Optional: name of the tool provider to target when the application
     * uses multiple tool providers. If not set, the default tool provider is used.
     */
    Optional<String> toolProvider();
}
