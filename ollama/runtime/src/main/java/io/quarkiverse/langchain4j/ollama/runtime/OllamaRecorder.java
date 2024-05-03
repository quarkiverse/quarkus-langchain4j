package io.quarkiverse.langchain4j.ollama.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ollama.OllamaChatLanguageModel;
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel;
import io.quarkiverse.langchain4j.ollama.OllamaStreamingChatLanguageModel;
import io.quarkiverse.langchain4j.ollama.Options;
import io.quarkiverse.langchain4j.ollama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OllamaRecorder {

    public Supplier<ChatLanguageModel> chatModel(LangChain4jOllamaConfig runtimeConfig, String modelName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig, modelName);

        if (ollamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = ollamaConfig.chatModel();

            Options.Builder optionsBuilder = Options.builder()
                    .temperature(chatModelConfig.temperature())
                    .topK(chatModelConfig.topK())
                    .topP(chatModelConfig.topP())
                    .numPredict(chatModelConfig.numPredict());

            if (chatModelConfig.stop().isPresent()) {
                optionsBuilder.stop(chatModelConfig.stop().get());
            }
            if (chatModelConfig.seed().isPresent()) {
                optionsBuilder.seed(chatModelConfig.seed().get());
            }
            var builder = OllamaChatLanguageModel.builder()
                    .baseUrl(ollamaConfig.baseUrl())
                    .timeout(ollamaConfig.timeout())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
                    .model(chatModelConfig.modelId())
                    .format(chatModelConfig.format().orElse(null))
                    .options(optionsBuilder.build());

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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jOllamaConfig runtimeConfig, String modelName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig, modelName);

        if (ollamaConfig.enableIntegration()) {
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
                    .model(embeddingModelConfig.modelId())
                    .logRequests(embeddingModelConfig.logRequests().orElse(false))
                    .logResponses(embeddingModelConfig.logResponses().orElse(false));

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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jOllamaConfig runtimeConfig, String modelName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig, modelName);

        if (ollamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = ollamaConfig.chatModel();

            Options.Builder optionsBuilder = Options.builder()
                    .temperature(chatModelConfig.temperature())
                    .topK(chatModelConfig.topK())
                    .topP(chatModelConfig.topP())
                    .numPredict(chatModelConfig.numPredict());

            if (chatModelConfig.stop().isPresent()) {
                optionsBuilder.stop(chatModelConfig.stop().get());
            }
            if (chatModelConfig.seed().isPresent()) {
                optionsBuilder.seed(chatModelConfig.seed().get());
            }
            var builder = OllamaStreamingChatLanguageModel.builder()
                    .baseUrl(ollamaConfig.baseUrl())
                    .timeout(ollamaConfig.timeout())
                    .logRequests(ollamaConfig.logRequests().orElse(false))
                    .logResponses(ollamaConfig.logResponses().orElse(false))
                    .model(chatModelConfig.modelId())
                    .options(optionsBuilder.build());

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

    private LangChain4jOllamaConfig.OllamaConfig correspondingOllamaConfig(LangChain4jOllamaConfig runtimeConfig,
            String modelName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            ollamaConfig = runtimeConfig.defaultConfig();
        } else {
            ollamaConfig = runtimeConfig.namedConfig().get(modelName);
        }
        return ollamaConfig;
    }
}
