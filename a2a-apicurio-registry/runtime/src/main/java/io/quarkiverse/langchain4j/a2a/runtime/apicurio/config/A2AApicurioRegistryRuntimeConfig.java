package io.quarkiverse.langchain4j.a2a.runtime.apicurio.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.a2a.apicurio-registry")
public interface A2AApicurioRegistryRuntimeConfig {

    /**
     * The base URL of the Apicurio Registry instance
     * (e.g. http://localhost:8080/apis/registry/v3).
     */
    Optional<String> url();

    /**
     * Optional authentication token for the registry.
     */
    Optional<String> authToken();

    /**
     * Group ID to use when publishing and searching agent cards.
     */
    @WithDefault("default")
    String groupId();

    /**
     * The base URL of this application's A2A server endpoint, used when publishing the agent card.
     */
    Optional<String> agentUrl();
}
