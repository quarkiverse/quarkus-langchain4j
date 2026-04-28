package io.quarkiverse.langchain4j.mcp.runtime.apicurio.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.mcp.apicurio-registry")
public interface McpApicurioRegistryRuntimeConfig {

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
    @WithName("tool-provider")
    Optional<String> toolProvider();
}
