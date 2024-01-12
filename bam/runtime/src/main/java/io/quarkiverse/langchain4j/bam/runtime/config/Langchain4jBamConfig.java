package io.quarkiverse.langchain4j.bam.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.bam")
public interface Langchain4jBamConfig {

    /**
     * Base URL where the Ollama serving is running
     */
    @ConfigDocDefault("https://bam-api.res.ibm.com")
    Optional<URL> baseUrl();

    /**
     * BAM API key
     */
    String apiKey();

    /**
     * Timeout for BAM calls
     */
    @WithDefault("10s")
    Duration timeout();

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
     * Chat model related settings
     */
    ChatModelConfig chatModel();
}
