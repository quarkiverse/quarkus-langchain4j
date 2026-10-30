package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface TtsModelConfig {

    /**
     * The HuggingFace/ModelScope GGUF model reference.
     * Required unless {@link #modelPath()} is set.
     */
    Optional<String> model();

    /**
     * Path to a local GGUF file. Takes precedence over {@link #model()}; when set, no download occurs.
     */
    Optional<Path> modelPath();

    /**
     * The speech speed multiplier (must be greater than {@code 0}).
     */
    OptionalDouble speed();

    /**
     * The maximum number of input characters.
     */
    OptionalInt maxInputChars();
}
