package io.quarkiverse.langchain4j.llama3.runtime.config;

import io.quarkiverse.langchain4j.llama3.Consts;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelFixedRuntimeConfig {

    /**
     * Model name to use
     */
    @WithDefault(Consts.DEFAULT_CHAT_MODEL_NAME)
    String modelName();

    /**
     * Quantization of the model to use
     */
    @WithDefault(Consts.DEFAULT_CHAT_MODEL_QUANTIZATION)
    String quantization();

    /**
     * Llama3.java supports AOT model preloading, enabling 0-overhead, instant inference, with minimal TTFT
     * (time-to-first-token).
     * A specialized, larger binary will be generated, with no parsing overhead for that particular model.
     * It can still run other models, although incurring the usual parsing overhead.
     */
    @WithDefault("false")
    boolean preLoadInNative();
}
