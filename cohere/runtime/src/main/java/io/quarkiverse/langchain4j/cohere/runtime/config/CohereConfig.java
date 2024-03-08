package io.quarkiverse.langchain4j.cohere.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.cohere")
public interface CohereConfig {

    /**
     * Base URL of the Cohere API.
     */
    @WithDefault("https://api.cohere.ai/")
    String baseUrl();

    /**
     * Cohere API key.
     */
    String apiKey();

    /**
     * Scoring model config.
     */
    CohereScoringModelConfig scoringModel();
}
