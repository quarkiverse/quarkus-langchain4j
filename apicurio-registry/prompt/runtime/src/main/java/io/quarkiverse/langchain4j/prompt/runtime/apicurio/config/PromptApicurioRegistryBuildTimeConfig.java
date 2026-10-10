package io.quarkiverse.langchain4j.prompt.runtime.apicurio.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.prompt.apicurio-registry")
public interface PromptApicurioRegistryBuildTimeConfig {

    /**
     * Whether the Apicurio Registry prompt template integration is enabled.
     */
    @WithDefault("true")
    boolean enabled();
}
