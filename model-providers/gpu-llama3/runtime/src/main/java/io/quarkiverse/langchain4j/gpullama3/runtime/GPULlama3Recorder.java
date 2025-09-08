package io.quarkiverse.langchain4j.gpullama3.runtime;

import java.nio.file.Paths;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.gpullama3.GPULlama3ChatModel;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.LangChain4jGPULlama3Config;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.LangChain4jGPULlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class GPULlama3Recorder {

    private static final Logger LOG = Logger.getLogger(GPULlama3Recorder.class);

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

            return () -> {
                try {
                    LOG.info("Creating GPULlama3ChatModel for config: " + configName);

                    // Create the model normally
                    var builder = GPULlama3ChatModel.builder()
                            .modelPath(Paths.get(modelPath));

                    chatModelConfig.temperature().ifPresent(builder::temperature);
                    chatModelConfig.maxTokens().ifPresent(builder::maxTokens);

                    LOG.debug("Building GPULlama3ChatModel with path: " + modelPath);
                    return builder.build();

                } catch (Exception e) {
                    LOG.error("Error creating GPULlama3ChatModel", e);
                    return new FallbackChatModel(
                            "Error: " + e.getMessage() + "\n\n");
                }
            };
        } else {
            return () -> new FallbackChatModel("GPULlama3 integration is disabled");
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

    private boolean inDebugMode() {
        return LOG.isDebugEnabled();
    }

    /**
     * A simple fallback implementation of ChatModel that returns a message
     * explaining why the real model is unavailable.
     */
    private static class FallbackChatModel implements ChatModel {
        private final String reason;

        FallbackChatModel(String reason) {
            this.reason = reason;
        }

        @Override
        public String chat(String message) {
            return "GPULlama3 model is unavailable: " + reason +
                    "\n\nThis is a fallback response to: " + message;
        }
    }

}
