package io.quarkiverse.langchain4j.typesafe.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.typesafe")
public interface LangChain4jTypeSafeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    TypeSafeConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, TypeSafeConfig> namedConfig();

    @ConfigGroup
    interface TypeSafeConfig {

        /**
         * Base URL of the System One API endpoint.
         */
        @WithDefault("https://api.typesafe.ai/")
        String baseUrl();

        /**
         * TypeSafe API key (required for cloud endpoint, optional for self-hosted).
         */
        Optional<String> apiKey();

        /**
         * Decision model settings.
         */
        DecisionModelConfig decisionModel();
    }
}
