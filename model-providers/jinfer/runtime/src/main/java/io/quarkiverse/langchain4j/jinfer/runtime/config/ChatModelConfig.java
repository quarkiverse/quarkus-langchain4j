package io.quarkiverse.langchain4j.jinfer.runtime.config;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

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
     * The context window size in tokens. {@code 0} uses the model's full context.
     */
    OptionalInt contextCapacity();

    /**
     * The sampling temperature.
     */
    @WithDefault("${quarkus.langchain4j.temperature:0.7}")
    Double temperature();

    /**
     * The nucleus sampling {@code top-p} value.
     */
    OptionalDouble topP();

    /**
     * The {@code top-k} sampling value.
     */
    OptionalInt topK();

    /**
     * The {@code min-p} sampling value.
     */
    OptionalDouble minP();

    /**
     * The maximum number of tokens to generate.
     */
    OptionalInt maxOutputTokens();

    /**
     * The maximum number of reasoning tokens. {@code -1} uncaps reasoning.
     */
    OptionalInt maxReasoningTokens();

    /**
     * The RNG seed for sampling.
     */
    OptionalLong seed();

    /**
     * Path to a read-only cached-prompt artifact to mount.
     */
    Optional<Path> promptCache();

    /**
     * Whether to enable the model's "thinking"/reasoning mode.
     */
    Optional<Boolean> thinking();
}
