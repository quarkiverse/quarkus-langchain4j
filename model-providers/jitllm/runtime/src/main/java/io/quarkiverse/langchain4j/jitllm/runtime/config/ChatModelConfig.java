package io.quarkiverse.langchain4j.jitllm.runtime.config;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.beehive.jitllm.api.ReasoningEffort;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * What sampling temperature to use, between 0.0 and 1.0.
     */
    @ConfigDocDefault("0.3")
    @WithDefault("${quarkus.langchain4j.temperature}")
    OptionalDouble temperature();

    /**
     * What sampling topP to use, between 0.0 and 1.0.
     */
    @ConfigDocDefault("0.85")
    @WithDefault("${quarkus.langchain4j.top-p}")
    OptionalDouble topP();

    /**
     * What seed value to use.
     *
     * @return
     */
    @ConfigDocDefault("1234")
    @WithDefault("${quarkus.langchain4j.seed}")
    OptionalInt seed();

    /**
     * The maximum number of tokens to generate in the completion.
     */
    @ConfigDocDefault("512")
    OptionalInt maxTokens();

    /**
     * Whether to choose the prefill/decode execution policy explicitly instead of leaving it to
     * the engine. When {@code true}, the prompt is processed in a dedicated prefill phase of
     * {@link #prefillBatchSize()} tokens per step before single-token decoding, and the vendor
     * native libraries (cuBLAS/cuDNN, MLX) are not used.
     * <p>
     * Off by default, which lets the engine pick the execution policy for the model and device:
     * on GPU that is batched prefill (512 tokens per step where the model supports it) with the
     * native libraries where they are installed.
     */
    @WithDefault("false")
    boolean prefillDecode();

    /**
     * Number of prompt tokens processed per batch during the prefill phase. Only used when
     * {@link #prefillDecode()} is {@code true}: a value greater than 1 enables the batched
     * prefill path, while 1 falls back to sequential prefill/decode. When
     * {@link #prefillDecode()} is {@code false} the engine chooses the batch size.
     */
    @WithDefault("1")
    int prefillBatchSize();

    /**
     * Whether to enable the model's thinking/reasoning phase. Only models with a thinking mode
     * (e.g. Qwen3) honor this; for other models it has no effect.
     * <p>
     * When {@code true}, the model reasons inside
     * {@code <think>…</think>} before answering, which tends to improve tool-calling decisions.
     * Set to {@code false} for faster responses by skipping the reasoning phase.
     */
    @WithDefault("false")
    boolean enableThinking();

    /**
     * How long the model reasons before answering: {@code xhigh}, {@code medium} or {@code low}.
     * Only models whose chat template takes a reasoning effort (e.g. Qwen3.8) honor this, and only
     * with {@link #enableThinking()} set; otherwise it has no effect. When unset, the model's own
     * default applies, which for Qwen3.8 is the longest reasoning.
     */
    @ConfigDocDefault("the model's default")
    Optional<ReasoningEffort> reasoningEffort();

    /**
     * Amount of GPU memory TornadoVM is allowed to allocate e.g. {@code "7GB"}, {@code "512MB"}.
     * <p>
     * Note: this maps to the JVM-global engine flag {@code tornado.device.memory}, so when
     * multiple models are configured the first one to initialize wins.
     */
    @ConfigDocDefault("4GB")
    Optional<String> deviceMemory();
}