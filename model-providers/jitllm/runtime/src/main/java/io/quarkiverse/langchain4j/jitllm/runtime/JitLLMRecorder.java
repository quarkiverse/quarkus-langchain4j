package io.quarkiverse.langchain4j.jitllm.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.jitllm.JitLLMChatModel;
import io.quarkiverse.langchain4j.jitllm.JitLLMModelHolder;
import io.quarkiverse.langchain4j.jitllm.JitLLMStreamingChatModel;
import io.quarkiverse.langchain4j.jitllm.runtime.config.LangChain4jJitLLMFixedRuntimeConfig;
import io.quarkiverse.langchain4j.jitllm.runtime.config.LangChain4jJitLLMRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JitLLMRecorder {

    private static final Logger LOG = Logger.getLogger(JitLLMRecorder.class);

    private final RuntimeValue<LangChain4jJitLLMRuntimeConfig> runtimeConfig;
    private final RuntimeValue<LangChain4jJitLLMFixedRuntimeConfig> fixedRuntimeConfig;

    // One holder per config name — shared between ChatModel and StreamingChatModel
    private final ConcurrentHashMap<String, JitLLMModelHolder> modelHolders = new ConcurrentHashMap<>();

    public JitLLMRecorder(RuntimeValue<LangChain4jJitLLMRuntimeConfig> runtimeConfig,
            RuntimeValue<LangChain4jJitLLMFixedRuntimeConfig> fixedRuntimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.fixedRuntimeConfig = fixedRuntimeConfig;
    }

    /**
     * Closes every loaded model once, at application shutdown.
     *
     * <p>
     * The holder is the owner: it loads the model and opens the one session both beans generate
     * through, so it is the only thing entitled to close them. Requests borrow the session and do
     * not own it.
     *
     * <p>
     * Before the façade migration nothing closed anything here — the TornadoVM plan and its
     * device copy of the weights leaked until the JVM exited.
     */
    public void closeModelsAtShutdown(ShutdownContext shutdown) {
        shutdown.addLastShutdownTask(() -> {
            modelHolders.values().forEach(holder -> {
                try {
                    holder.close();
                } catch (RuntimeException e) {
                    // One holder failing must not leave the others open.
                    LOG.warn("Failed to close a JitLLM model at shutdown", e);
                }
            });
            modelHolders.clear();
        });
    }

    public Supplier<ChatModel> chatModel(String configName) {
        var jitllmConfig = correspondingConfig(configName);

        if (jitllmConfig.enableIntegration()) {
            LOG.debugf("Registering JitLLMChatModel CDI Bean for config: %s", configName);
            return () -> JitLLMChatModel.create(getOrCreateHolder(configName));
        } else {
            return () -> new DisabledChatModel();
        }
    }

    public Supplier<StreamingChatModel> streamingChatModel(String configName) {
        var jitllmConfig = correspondingConfig(configName);

        if (jitllmConfig.enableIntegration()) {
            LOG.debugf("Registering JitLLMStreamingChatModel CDI Bean for config: %s", configName);
            return () -> JitLLMStreamingChatModel.create(getOrCreateHolder(configName));
        } else {
            return () -> new DisabledStreamingChatModel();
        }
    }

    private JitLLMModelHolder getOrCreateHolder(String configName) {
        return modelHolders.computeIfAbsent(configName, k -> {
            var chatModelConfig = correspondingConfig(configName).chatModel();
            var fixedConfig = correspondingFixedConfig(configName);

            return new JitLLMModelHolder(
                    fixedRuntimeConfig.getValue().modelsPath(),
                    fixedConfig.chatModel().modelName(),
                    fixedConfig.chatModel().quantization(),
                    chatModelConfig.temperature().isPresent() ? chatModelConfig.temperature().getAsDouble() : null,
                    chatModelConfig.topP().isPresent() ? chatModelConfig.topP().getAsDouble() : null,
                    chatModelConfig.seed().isPresent() ? chatModelConfig.seed().getAsInt() : null,
                    chatModelConfig.maxTokens().isPresent() ? chatModelConfig.maxTokens().getAsInt() : null,
                    Boolean.TRUE,
                    chatModelConfig.prefillDecode(),
                    chatModelConfig.prefillBatchSize(),
                    chatModelConfig.enableThinking(),
                    chatModelConfig.deviceMemory().orElse(null));
        });
    }

    private LangChain4jJitLLMRuntimeConfig.JitLLMConfig correspondingConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private LangChain4jJitLLMFixedRuntimeConfig.JitLLMConfig correspondingFixedConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? fixedRuntimeConfig.getValue().defaultConfig()
                : fixedRuntimeConfig.getValue().namedConfig().get(configName);
    }

    private boolean inDebugMode() {
        return LOG.isDebugEnabled();
    }

}
