package io.quarkiverse.langchain4j.a2a.server.runtime.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.a2a.server")
public interface A2AServerRuntimeConfiguration {

    /**
     * The name under which the agent advertises itself on its Agent Card.
     */
    @WithDefault("${quarkus.application.name}")
    String name();

    /**
     * A human-readable description of what the agent does, used by clients to decide whether to engage with it.
     */
    @ConfigDocDefault("empty")
    Optional<String> description();

    /**
     * The URL where the agent is available. When not set, it is derived from the address the HTTP server is
     * actually bound to, preferring HTTPS when both are enabled.
     */
    @ConfigDocDefault("the address the HTTP server is bound to")
    Optional<String> url();

    /**
     * The version of the agent
     */
    @WithDefault("1.0.0")
    String version();

    /**
     * The identity of each agent exposed under a tenant, keyed by the tenant named on {@code @ExposeA2AAgent}.
     */
    @ConfigDocSection
    @ConfigDocMapKey("tenant")
    Map<String, TenantConfiguration> tenants();

    /**
     * Every value left unset falls back to the one configured for the server as a whole.
     */
    interface TenantConfiguration {

        /**
         * The name under which the agent advertises itself on its Agent Card.
         */
        @ConfigDocDefault("quarkus.langchain4j.a2a.server.name")
        Optional<String> name();

        /**
         * A human-readable description of what the agent does.
         */
        @ConfigDocDefault("quarkus.langchain4j.a2a.server.description")
        Optional<String> description();

        /**
         * The URL where the agent is available.
         */
        @ConfigDocDefault("quarkus.langchain4j.a2a.server.url")
        Optional<String> url();

        /**
         * The version of the agent.
         */
        @ConfigDocDefault("quarkus.langchain4j.a2a.server.version")
        Optional<String> version();
    }
}
