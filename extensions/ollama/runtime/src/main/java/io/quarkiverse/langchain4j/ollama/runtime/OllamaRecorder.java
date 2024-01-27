package io.quarkiverse.langchain4j.ollama.runtime;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.ollama.OllamaChatLanguageModel;
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel;
import io.quarkiverse.langchain4j.ollama.Options;
import io.quarkiverse.langchain4j.ollama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.Langchain4jOllamaConfig;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OllamaRecorder {

    public Supplier<?> chatModel(Langchain4jOllamaConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        Options.Builder optionsBuilder = Options.builder()
                .temperature(chatModelConfig.temperature())
                .topK(chatModelConfig.topK())
                .topP(chatModelConfig.topP())
                .numPredict(chatModelConfig.numPredict());
        if (chatModelConfig.stop().isPresent()) {
            optionsBuilder.stop(chatModelConfig.stop().get());
        }
        var builder = OllamaChatLanguageModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .model(chatModelConfig.modelId())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())
                .options(optionsBuilder.build());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> embeddingModel(Langchain4jOllamaConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        Options.Builder optionsBuilder = Options.builder()
                .temperature(chatModelConfig.temperature())
                .topK(chatModelConfig.topK())
                .topP(chatModelConfig.topP())
                .numPredict(chatModelConfig.numPredict());
        if (chatModelConfig.stop().isPresent()) {
            optionsBuilder.stop(chatModelConfig.stop().get());
        }
        var builder = OllamaEmbeddingModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .model(chatModelConfig.modelId());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }
}
