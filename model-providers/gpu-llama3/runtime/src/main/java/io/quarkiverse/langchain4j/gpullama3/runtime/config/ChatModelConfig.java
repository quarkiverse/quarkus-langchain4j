package io.quarkiverse.langchain4j.gpullama3.runtime.config;

import java.util.OptionalDouble;
import java.util.OptionalInt;

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
     * Whether to use the prefill/decode inference path, which processes the prompt in a
     * dedicated prefill phase before single-token decoding. Combined with a
     * {@link #prefillBatchSize()} greater than 1 this enables the batched prefill path,
     * which significantly speeds up prompt processing on the GPU.
     * <p>
     * Note: this maps to the JVM-global engine flags {@code llama.withPrefillDecode} /
     * {@code llama.prefillBatchSize}, so when multiple models are configured the first one
     * to initialize wins.
     */
    @WithDefault("true")
    boolean prefillDecode();

    /**
     * Number of prompt tokens processed per batch during the prefill phase. Only used when
     * {@link #prefillDecode()} is {@code true}: a value greater than 1 enables the batched
     * prefill path, while 1 falls back to sequential prefill/decode.
     */
    @WithDefault("32")
    int prefillBatchSize();

    /**
     * Whether to enable the model's thinking/reasoning phase. Only models with a thinking mode
     * (e.g. Qwen3) honor this; for other models it has no effect.
     * <p>
     * When {@code true} (the upstream Qwen3 default), the model reasons inside
     * {@code <think>…</think>} before answering, which tends to improve tool-calling decisions.
     * Set to {@code false} for faster responses by skipping the reasoning phase.
     */
    @WithDefault("true")
    boolean enableThinking();
}