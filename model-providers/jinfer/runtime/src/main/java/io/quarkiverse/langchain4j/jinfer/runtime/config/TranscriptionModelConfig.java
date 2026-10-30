package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface TranscriptionModelConfig {

    /**
     * The HuggingFace/ModelScope GGUF model reference.
     * Required unless {@link #modelPath()} is set.
     */
    Optional<String> model();

    /**
     * Path to a local GGUF file. Takes precedence over {@link #model()}; when set, no download occurs.
     */
    Optional<Path> modelPath();
}
