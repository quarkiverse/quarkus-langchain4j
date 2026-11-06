package io.quarkiverse.langchain4j.a2a.runtime.apicurio.config;

import java.util.List;
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
     * Not supported: setting it fails at startup. It was never applied, so a configured token was
     * silently ignored. Use the client credentials or basic authentication settings under
     * {@code auth} instead.
     *
     * @deprecated use {@code auth.client-id}, {@code auth.client-secret} and {@code auth.token-endpoint},
     *             or {@code auth.username} and {@code auth.password}
     */
    @Deprecated(forRemoval = true)
    Optional<String> authToken();

    /**
     * Credentials for the registry, used to publish agent cards and to discover agents.
     */
    RegistryAuthConfig auth();

    /**
     * Group ID to use when publishing and searching agent cards.
     */
    @WithDefault("default")
    String groupId();

    /**
     * The base URL of this application's A2A server endpoint, used when publishing the agent card.
     */
    Optional<String> agentUrl();

    /**
     * The input keys used for agents discovered from the registry whose agent card artifact does not
     * declare an {@code a2a-input-keys} label.
     * <p>
     * A2A agent cards carry no input schema, so discovered agents are built as untyped agents and these
     * keys define both the arguments exposed to a supervisor or planner and the keys read from the
     * {@code AgenticScope} when invoking the remote agent.
     */
    @WithDefault("input")
    List<String> defaultInputKeys();
}
