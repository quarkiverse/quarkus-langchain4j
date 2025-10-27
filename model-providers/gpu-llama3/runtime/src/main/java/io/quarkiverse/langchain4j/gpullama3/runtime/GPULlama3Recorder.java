package io.quarkiverse.langchain4j.gpullama3.runtime;

import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import io.quarkiverse.langchain4j.gpullama3.GPULlama3ChatModel;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.LangChain4jGPULlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.LangChain4jGPULlama3RuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class GPULlama3Recorder {

    private static final Logger LOG = Logger.getLogger(GPULlama3Recorder.class);

    private final RuntimeValue<LangChain4jGPULlama3RuntimeConfig> runtimeConfig;
    private final RuntimeValue<LangChain4jGPULlama3FixedRuntimeConfig> fixedRuntimeConfig;

    public GPULlama3Recorder(RuntimeValue<LangChain4jGPULlama3RuntimeConfig> runtimeConfig,
            RuntimeValue<LangChain4jGPULlama3FixedRuntimeConfig> fixedRuntimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.fixedRuntimeConfig = fixedRuntimeConfig;
    }

    public Supplier<ChatModel> chatModel(String configName) {
        var gpuLlama3Config = correspondingConfig(configName);
        var gpuLlama3FixedRuntimeConfig = correspondingFixedConfig(configName);

        if (gpuLlama3Config.enableIntegration()) {
            LOG.info("Creating GPULlama3ChatModel for config: " + configName);
            var chatModelConfig = gpuLlama3Config.chatModel();

            var builder = GPULlama3ChatModel.builder()
                    .modelName(gpuLlama3FixedRuntimeConfig.chatModel().modelName())
                    .quantization(gpuLlama3FixedRuntimeConfig.chatModel().quantization())
                    .onGPU(Boolean.TRUE)
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().getAsInt());
            }
            if (chatModelConfig.seed().isPresent()) {
                builder.seed(chatModelConfig.seed().getAsInt());
            }

            return new Supplier<>() {
                @Override
                public ChatModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public ChatModel get() {
                    return new DisabledChatModel();
                }
            };
        }
    }

    private LangChain4jGPULlama3RuntimeConfig.GPULlama3Config correspondingConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private LangChain4jGPULlama3FixedRuntimeConfig.GPULlama3Config correspondingFixedConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? fixedRuntimeConfig.getValue().defaultConfig()
                : fixedRuntimeConfig.getValue().namedConfig().get(configName);
    }

    private boolean inDebugMode() {
        return LOG.isDebugEnabled();
    }

}
