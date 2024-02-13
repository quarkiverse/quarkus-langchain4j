package io.quarkiverse.langchain4j.bam.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.net.URL;
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
@ConfigMapping(prefix = "quarkus.langchain4j.bam")
public interface Langchain4jBamConfig {

    /**
     * Default model config.
     */
    @WithParentName
    BamConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, BamConfig> namedConfig();

    @ConfigGroup
    interface BamConfig {
        /**
         * Base URL where the Ollama serving is running
         */
        @ConfigDocDefault("https://bam-api.res.ibm.com")
        Optional<URL> baseUrl();

        /**
         * BAM API key
         */
        @WithDefault("dummy") // TODO: this should be optional but Smallrye Config doesn't like it
        String apiKey();

        /**
         * Timeout for BAM calls
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * Version to use
         */
        @WithDefault("2024-01-10")
        String version();

        /**
         * Whether the BAM client should log requests
         */
        @WithDefault("false")
        Boolean logRequests();

        /**
         * Whether the BAM client should log responses
         */
        @WithDefault("false")
        Boolean logResponses();

        /**
         * Whether or not to enable the integration. Defaults to {@code true}, which means requests are made to the OpenAI
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings
         */
        EmbeddingModelConfig embeddingModel();
    }
}
