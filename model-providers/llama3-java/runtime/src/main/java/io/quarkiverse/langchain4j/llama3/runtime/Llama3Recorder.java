package io.quarkiverse.langchain4j.llama3.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import io.quarkiverse.langchain4j.llama3.Llama3ChatModel;
import io.quarkiverse.langchain4j.llama3.Llama3StreamingChatModel;
import io.quarkiverse.langchain4j.llama3.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3RuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class Llama3Recorder {

    public Supplier<ChatLanguageModel> chatModel(LangChain4jLlama3RuntimeConfig runtimeConfig,
            LangChain4jLlama3FixedRuntimeConfig fixedRuntimeConfig,
            String configName) {
        LangChain4jLlama3RuntimeConfig.Llama3Config llama3Config = correspondingJlamaConfig(runtimeConfig, configName);
        LangChain4jLlama3FixedRuntimeConfig.Llama3Config llama3FixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                fixedRuntimeConfig, configName);

        if (llama3Config.enableIntegration()) {
            ChatModelConfig chatModelConfig = llama3Config.chatModel();

            var builder = Llama3ChatModel.builder()
                    .modelName(llama3FixedRuntimeConfig.chatModel().modelName())
                    .quantization(llama3FixedRuntimeConfig.chatModel().quantization())
                    .logRequests(llama3Config.logRequests().orElse(false))
                    .logResponses(llama3Config.logResponses().orElse(false))
                    .modelCachePath(fixedRuntimeConfig.modelsPath());

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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jLlama3RuntimeConfig runtimeConfig,
            LangChain4jLlama3FixedRuntimeConfig fixedRuntimeConfig,
            String configName) {
        LangChain4jLlama3RuntimeConfig.Llama3Config llama3Config = correspondingJlamaConfig(runtimeConfig, configName);
        LangChain4jLlama3FixedRuntimeConfig.Llama3Config llama3FixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                fixedRuntimeConfig, configName);

        if (llama3Config.enableIntegration()) {
            ChatModelConfig chatModelConfig = llama3Config.chatModel();

            var builder = Llama3StreamingChatModel.builder()
                    .modelName(llama3FixedRuntimeConfig.chatModel().modelName())
                    .quantization(llama3FixedRuntimeConfig.chatModel().quantization())
                    .logRequests(llama3Config.logRequests().orElse(false))
                    .logResponses(llama3Config.logResponses().orElse(false))
                    .modelCachePath(fixedRuntimeConfig.modelsPath());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature((float) chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().getAsInt());
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

    private LangChain4jLlama3RuntimeConfig.Llama3Config correspondingJlamaConfig(
            LangChain4jLlama3RuntimeConfig runtimeConfig,
            String configName) {
        LangChain4jLlama3RuntimeConfig.Llama3Config llama3Config;
        if (NamedConfigUtil.isDefault(configName)) {
            llama3Config = runtimeConfig.defaultConfig();
        } else {
            llama3Config = runtimeConfig.namedConfig().get(configName);
        }
        return llama3Config;
    }

    private LangChain4jLlama3FixedRuntimeConfig.Llama3Config correspondingJlamaFixedRuntimeConfig(
            LangChain4jLlama3FixedRuntimeConfig runtimeConfig,
            String configName) {
        LangChain4jLlama3FixedRuntimeConfig.Llama3Config llama3Config;
        if (NamedConfigUtil.isDefault(configName)) {
            llama3Config = runtimeConfig.defaultConfig();
        } else {
            llama3Config = runtimeConfig.namedConfig().get(configName);
        }
        return llama3Config;
    }

}
