package io.quarkiverse.langchain4j.gpullama3.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.gpullama3.GPULlama3ChatModel;
import io.quarkiverse.langchain4j.gpullama3.GPULlama3ModelHolder;
import io.quarkiverse.langchain4j.gpullama3.GPULlama3StreamingChatModel;
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

    // One holder per config name — shared between ChatModel and StreamingChatModel
    private final ConcurrentHashMap<String, GPULlama3ModelHolder> modelHolders = new ConcurrentHashMap<>();

    public GPULlama3Recorder(RuntimeValue<LangChain4jGPULlama3RuntimeConfig> runtimeConfig,
            RuntimeValue<LangChain4jGPULlama3FixedRuntimeConfig> fixedRuntimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.fixedRuntimeConfig = fixedRuntimeConfig;
    }

    public Supplier<ChatModel> chatModel(String configName) {
        var gpuLlama3Config = correspondingConfig(configName);

        if (gpuLlama3Config.enableIntegration()) {
            LOG.info("Registering GPULlama3ChatModel CDI Bean for config: " + configName);
            return () -> GPULlama3ChatModel.create(getOrCreateHolder(configName));
        } else {
            return () -> new DisabledChatModel();
        }
    }

    public Supplier<StreamingChatModel> streamingChatModel(String configName) {
        var gpuLlama3Config = correspondingConfig(configName);

        if (gpuLlama3Config.enableIntegration()) {
            LOG.info("Registering GPULlama3StreamingChatModel CDI Bean for config: " + configName);
            return () -> GPULlama3StreamingChatModel.create(getOrCreateHolder(configName));
        } else {
            return () -> new DisabledStreamingChatModel();
        }
    }

    private GPULlama3ModelHolder getOrCreateHolder(String configName) {
        return modelHolders.computeIfAbsent(configName, k -> {
            var chatModelConfig = correspondingConfig(configName).chatModel();
            var fixedConfig = correspondingFixedConfig(configName);

            return new GPULlama3ModelHolder(
                    fixedRuntimeConfig.getValue().modelsPath(),
                    fixedConfig.chatModel().modelName(),
                    fixedConfig.chatModel().quantization(),
                    chatModelConfig.temperature().isPresent() ? chatModelConfig.temperature().getAsDouble() : null,
                    chatModelConfig.topP().isPresent() ? chatModelConfig.topP().getAsDouble() : null,
                    chatModelConfig.seed().isPresent() ? chatModelConfig.seed().getAsInt() : null,
                    chatModelConfig.maxTokens().isPresent() ? chatModelConfig.maxTokens().getAsInt() : null,
                    Boolean.TRUE);
        });
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
