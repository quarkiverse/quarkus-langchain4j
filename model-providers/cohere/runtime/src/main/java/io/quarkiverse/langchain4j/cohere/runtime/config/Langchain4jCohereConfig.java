package io.quarkiverse.langchain4j.cohere.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.cohere")
public interface Langchain4jCohereConfig {

    /**
     * Default model config.
     */
    @WithParentName
    CohereConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, CohereConfig> namedConfig();

    @ConfigGroup
    interface CohereConfig {

        /**
         * Base URL of the Cohere API.
         */
        @WithDefault("https://api.cohere.com/")
        String baseUrl();

        /**
         * Cohere API key.
         */
        @WithDefault("dummy")
        String apiKey();

        /**
         * Timeout for Cohere calls.
         */
        @ConfigDocDefault("30s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * Scoring model config.
         */
        ScoringModelConfig scoringModel();
    }
}
