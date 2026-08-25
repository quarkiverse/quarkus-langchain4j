package io.quarkiverse.langchain4j.a2a.runtime.apicurio.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.a2a.apicurio-registry")
public interface A2AApicurioRegistryBuildTimeConfig {

    /**
     * Whether the Apicurio Registry A2A agent discovery integration is enabled.
     */
    @WithDefault("true")
    boolean enabled();
}
