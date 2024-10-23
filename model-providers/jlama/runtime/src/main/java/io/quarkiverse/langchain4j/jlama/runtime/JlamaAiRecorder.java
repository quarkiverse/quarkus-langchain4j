package io.quarkiverse.langchain4j.jlama.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.jlama.JlamaChatModel;
import io.quarkiverse.langchain4j.jlama.JlamaEmbeddingModel;
import io.quarkiverse.langchain4j.jlama.JlamaStreamingChatModel;
import io.quarkiverse.langchain4j.jlama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaFixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JlamaAiRecorder {

    public Supplier<ChatLanguageModel> chatModel(LangChain4jJlamaConfig runtimeConfig,
            LangChain4jJlamaFixedRuntimeConfig fixedRuntimeConfig,
            String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(runtimeConfig, configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                fixedRuntimeConfig, configName);

        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            String modelName = jlamaFixedRuntimeConfig.chatModel().modelName();
            var builder = JlamaChatModel.builder()
                    .modelName(modelName)
                    .modelCachePath(fixedRuntimeConfig.modelsPath());

            jlamaConfig.logRequests().ifPresent(builder::logRequests);
            jlamaConfig.logResponses().ifPresent(builder::logResponses);

            chatModelConfig.temperature().ifPresent(temp -> builder.temperature((float) temp));
            chatModelConfig.maxTokens().ifPresent(builder::maxTokens);

            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return new DisabledChatLanguageModel();
                }
            };
        }
    }

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jJlamaConfig runtimeConfig,
            LangChain4jJlamaFixedRuntimeConfig fixedRuntimeConfig,
            String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(runtimeConfig, configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                fixedRuntimeConfig, configName);

        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            var builder = JlamaStreamingChatModel.builder()
                    .modelName(jlamaFixedRuntimeConfig.chatModel().modelName())
                    .modelCachePath(fixedRuntimeConfig.modelsPath());

            chatModelConfig.temperature().ifPresent(temp -> builder.temperature((float) temp));

            return new Supplier<>() {
                @Override
                public StreamingChatLanguageModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public StreamingChatLanguageModel get() {
                    return new DisabledStreamingChatLanguageModel();
                }
            };
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jJlamaConfig runtimeConfig,
            LangChain4jJlamaFixedRuntimeConfig fixedRuntimeConfig,
            String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(runtimeConfig, configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                fixedRuntimeConfig, configName);

        if (jlamaConfig.enableIntegration()) {
            var builder = JlamaEmbeddingModel.builder()
                    .modelName(jlamaFixedRuntimeConfig.embeddingModel().modelName())
                    .modelCachePath(fixedRuntimeConfig.modelsPath());

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

    private LangChain4jJlamaConfig.JlamaConfig correspondingJlamaConfig(LangChain4jJlamaConfig runtimeConfig,
            String configName) {
        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.defaultConfig()
                : runtimeConfig.namedConfig().get(configName);
    }

    private LangChain4jJlamaFixedRuntimeConfig.JlamaConfig correspondingJlamaFixedRuntimeConfig(
            LangChain4jJlamaFixedRuntimeConfig runtimeConfig,
            String configName) {
        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.defaultConfig()
                : runtimeConfig.namedConfig().get(configName);
    }

}
