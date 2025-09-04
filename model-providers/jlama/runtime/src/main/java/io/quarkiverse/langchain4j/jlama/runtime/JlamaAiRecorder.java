package io.quarkiverse.langchain4j.jlama.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.jlama.JlamaChatModel;
import io.quarkiverse.langchain4j.jlama.JlamaEmbeddingModel;
import io.quarkiverse.langchain4j.jlama.JlamaStreamingChatModel;
import io.quarkiverse.langchain4j.jlama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaFixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JlamaAiRecorder {
    private final RuntimeValue<LangChain4jJlamaConfig> runtimeConfig;
    private final RuntimeValue<LangChain4jJlamaFixedRuntimeConfig> fixedRuntimeConfig;

    public JlamaAiRecorder(RuntimeValue<LangChain4jJlamaConfig> runtimeConfig,
            RuntimeValue<LangChain4jJlamaFixedRuntimeConfig> fixedRuntimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.fixedRuntimeConfig = fixedRuntimeConfig;
    }

    public Supplier<ChatModel> chatModel(String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);

        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            String modelName = jlamaFixedRuntimeConfig.chatModel().modelName();
            var builder = JlamaChatModel.builder()
                    .modelName(modelName)
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            jlamaConfig.logRequests().ifPresent(builder::logRequests);
            jlamaConfig.logResponses().ifPresent(builder::logResponses);

            chatModelConfig.temperature().ifPresent(temp -> builder.temperature((float) temp));
            chatModelConfig.maxTokens().ifPresent(builder::maxTokens);

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

    public Supplier<StreamingChatModel> streamingChatModel(String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);
        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            var builder = JlamaStreamingChatModel.builder()
                    .modelName(jlamaFixedRuntimeConfig.chatModel().modelName())
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            chatModelConfig.temperature().ifPresent(temp -> builder.temperature((float) temp));

            return new Supplier<>() {
                @Override
                public StreamingChatModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public StreamingChatModel get() {
                    return new DisabledStreamingChatModel();
                }
            };
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);

        if (jlamaConfig.enableIntegration()) {
            var builder = JlamaEmbeddingModel.builder()
                    .modelName(jlamaFixedRuntimeConfig.embeddingModel().modelName())
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            return new Supplier<>() {
                @Override
                public EmbeddingModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public EmbeddingModel get() {
                    return new DisabledEmbeddingModel();
                }
            };
        }
    }

    private LangChain4jJlamaConfig.JlamaConfig correspondingJlamaConfig(String configName) {
        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private LangChain4jJlamaFixedRuntimeConfig.JlamaConfig correspondingJlamaFixedRuntimeConfig(
            String configName) {
        return NamedConfigUtil.isDefault(configName) ? fixedRuntimeConfig.getValue().defaultConfig()
                : fixedRuntimeConfig.getValue().namedConfig().get(configName);
    }

}
