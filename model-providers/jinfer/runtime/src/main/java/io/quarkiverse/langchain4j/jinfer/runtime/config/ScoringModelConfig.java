package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ScoringModelConfig {

    /**
     * The HuggingFace/ModelScope GGUF model reference, e.g. {@code owner/repo:Q8_0}.
     * Required unless {@link #modelPath()} is set.
     */
    Optional<String> model();

    /**
     * Path to a local GGUF file. Takes precedence over {@link #model()}; when set, no download occurs.
     */
    Optional<Path> modelPath();

    /**
     * The context window size in tokens.
     */
    OptionalInt contextCapacity();

    /**
     * An optional reranker instruction. Rejected if the reranker has no instruction slot.
     */
    Optional<String> instruction();
}
