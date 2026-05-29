package io.quarkiverse.langchain4j.openai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface AudioTranscriptionModelConfig {

    /**
     * Model name to use
     */
    @WithDefault("whisper-1")
    String modelName();

    /**
     * Whether audio transcription model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether audio transcription model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
