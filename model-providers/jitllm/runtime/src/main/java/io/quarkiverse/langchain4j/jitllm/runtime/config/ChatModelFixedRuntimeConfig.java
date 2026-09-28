package io.quarkiverse.langchain4j.jitllm.runtime.config;

import io.quarkiverse.langchain4j.jitllm.Consts;
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
}