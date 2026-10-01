package io.quarkiverse.langchain4j.typesafe.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface DecisionModelBuildConfig {

    /**
     * Whether the decision model should be enabled.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();
}
