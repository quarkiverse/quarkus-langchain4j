package io.quarkiverse.langchain4j.watsonx.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface GatewayImageModelBuildConfig {

    /**
     * Whether the gateway image model should be enabled.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();
}
