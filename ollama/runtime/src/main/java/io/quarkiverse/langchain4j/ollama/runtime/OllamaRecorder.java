package io.quarkiverse.langchain4j.ollama.runtime;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.ollama.OllamaChatLanguageModel;
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel;
import io.quarkiverse.langchain4j.ollama.Options;
import io.quarkiverse.langchain4j.ollama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.Langchain4jOllamaConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OllamaRecorder {

    public Supplier<?> chatModel(Langchain4jOllamaConfig runtimeConfig, String modelName) {
        Langchain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig, modelName);
        ChatModelConfig chatModelConfig = ollamaConfig.chatModel();
        Options.Builder optionsBuilder = Options.builder()
                .temperature(chatModelConfig.temperature())
                .topK(chatModelConfig.topK())
                .topP(chatModelConfig.topP())
                .numPredict(chatModelConfig.numPredict());
        if (chatModelConfig.stop().isPresent()) {
            optionsBuilder.stop(chatModelConfig.stop().get());
        }
        var builder = OllamaChatLanguageModel.builder()
                .baseUrl(ollamaConfig.baseUrl())
                .timeout(ollamaConfig.timeout())
                .logRequests(ollamaConfig.logRequests())
                .logResponses(ollamaConfig.logResponses())
                .model(chatModelConfig.modelId())
                .options(optionsBuilder.build());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> embeddingModel(Langchain4jOllamaConfig runtimeConfig, String modelName) {
        Langchain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig, modelName);
        EmbeddingModelConfig embeddingModelConfig = ollamaConfig.embeddingModel();
        Options.Builder optionsBuilder = Options.builder()
                .temperature(embeddingModelConfig.temperature())
                .topK(embeddingModelConfig.topK())
                .topP(embeddingModelConfig.topP())
                .numPredict(embeddingModelConfig.numPredict());
        if (embeddingModelConfig.stop().isPresent()) {
            optionsBuilder.stop(embeddingModelConfig.stop().get());
        }
        var builder = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaConfig.baseUrl())
                .timeout(ollamaConfig.timeout())
                .model(embeddingModelConfig.modelId());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private Langchain4jOllamaConfig.OllamaConfig correspondingOllamaConfig(Langchain4jOllamaConfig runtimeConfig,
            String modelName) {
        Langchain4jOllamaConfig.OllamaConfig ollamaConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            ollamaConfig = runtimeConfig.defaultConfig();
        } else {
            ollamaConfig = runtimeConfig.namedConfig().get(modelName).ollama();
        }
        return ollamaConfig;
    }
}
