package io.quarkiverse.langchain4j.a2a.server.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.a2a.client")
public interface A2AClientRuntimeConfiguration {

    /**
     * The URL where the agent is available
     */
    @ConfigDocDefault("http://${quarkus.http.host}:${quarkus.http.port}")
    Optional<String> url();

    /**
     * The version of the agent
     */
    @WithDefault("1.0.0")
    String version();
}
