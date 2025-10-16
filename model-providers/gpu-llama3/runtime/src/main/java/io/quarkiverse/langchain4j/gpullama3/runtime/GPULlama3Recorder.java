package io.quarkiverse.langchain4j.gpullama3.runtime;

import java.nio.file.Paths;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import io.quarkiverse.langchain4j.gpullama3.GPULlama3ChatModel;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.LangChain4jGPULlama3Config;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.LangChain4jGPULlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class GPULlama3Recorder {

    private final RuntimeValue<LangChain4jGPULlama3Config> runtimeConfig;
    private final RuntimeValue<LangChain4jGPULlama3FixedRuntimeConfig> fixedRuntimeConfig;

    public GPULlama3Recorder(RuntimeValue<LangChain4jGPULlama3Config> runtimeConfig,
            RuntimeValue<LangChain4jGPULlama3FixedRuntimeConfig> fixedRuntimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.fixedRuntimeConfig = fixedRuntimeConfig;
    }

    public Supplier<ChatModel> chatModel(String configName) {
        var config = correspondingConfig(configName);
        var fixedConfig = correspondingFixedConfig(configName);

        if (config.enableIntegration()) {
            var chatModelConfig = config.chatModel();
            String modelPath = fixedConfig.chatModel().modelPath();

            var builder = GPULlama3ChatModel.builder()
                    .modelPath(Paths.get(modelPath));

            // Temperature expects Double, not float - no conversion needed
            chatModelConfig.temperature().ifPresent(builder::temperature);
            chatModelConfig.maxTokens().ifPresent(builder::maxTokens);

            // Note: GPULlama3ChatModel.Builder doesn't have logRequests/logResponses methods
            // If you need logging, you'll need to add these methods to the Builder class
            // For now, we'll just skip them

            return builder::build;
        } else {
            return DisabledChatModel::new;
        }
    }

    private LangChain4jGPULlama3Config.RuntimeConfig correspondingConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private LangChain4jGPULlama3FixedRuntimeConfig.FixedRuntimeConfig correspondingFixedConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? fixedRuntimeConfig.getValue().defaultConfig()
                : fixedRuntimeConfig.getValue().namedConfig().get(configName);
    }
}
