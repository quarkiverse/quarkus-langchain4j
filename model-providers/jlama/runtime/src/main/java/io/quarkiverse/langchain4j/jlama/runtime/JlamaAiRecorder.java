package io.quarkiverse.langchain4j.jlama.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.jlama.JlamaChatModel;
import dev.langchain4j.model.jlama.JlamaEmbeddingModel;
import dev.langchain4j.model.jlama.JlamaStreamingChatModel;
import io.quarkiverse.langchain4j.jlama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JlamaAiRecorder {

    public Supplier<ChatLanguageModel> chatModel(LangChain4jJlamaAiConfig runtimeConfig, String configName) {
        LangChain4jJlamaAiConfig.JlamaAiConfig jlamaConfig = correspondingJlamaConfig(runtimeConfig, configName);

        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            var builder = JlamaChatModel.builder().modelName(chatModelConfig.modelName());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature((float) chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().getAsInt());
            }

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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jJlamaAiConfig runtimeConfig,
            String configName) {
        LangChain4jJlamaAiConfig.JlamaAiConfig jlamaConfig = correspondingJlamaConfig(runtimeConfig, configName);

        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            var builder = JlamaStreamingChatModel.builder().modelName(chatModelConfig.modelName());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature((float) chatModelConfig.temperature().getAsDouble());
            }
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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jJlamaAiConfig runtimeConfig, String configName) {
        LangChain4jJlamaAiConfig.JlamaAiConfig jlamaConfig = correspondingJlamaConfig(runtimeConfig, configName);

        if (jlamaConfig.enableIntegration()) {
            EmbeddingModelConfig embeddingModelConfig = jlamaConfig.embeddingModel();
            var builder = JlamaEmbeddingModel.builder().modelName(embeddingModelConfig.modelName());

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

    private LangChain4jJlamaAiConfig.JlamaAiConfig correspondingJlamaConfig(LangChain4jJlamaAiConfig runtimeConfig,
            String configName) {
        LangChain4jJlamaAiConfig.JlamaAiConfig huggingFaceConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            huggingFaceConfig = runtimeConfig.defaultConfig();
        } else {
            huggingFaceConfig = runtimeConfig.namedConfig().get(configName);
        }
        return huggingFaceConfig;
    }

}
