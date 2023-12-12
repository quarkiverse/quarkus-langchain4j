package io.quarkiverse.langchain4j.localai.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.localai.LocalAiChatModel;
import dev.langchain4j.model.localai.LocalAiEmbeddingModel;
import dev.langchain4j.model.localai.LocalAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import io.quarkiverse.langchain4j.localai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.localai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.localai.runtime.config.Langchain4jLocalAiConfig;
import io.quarkiverse.langchain4j.localai.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class LocalAiRecorder {

    public Supplier<?> chatModel(Langchain4jLocalAiConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = LocalAiChatModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .modelName(chatModelConfig.modelName())
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP());

        if (chatModelConfig.maxTokens().isPresent()) {
            builder.maxTokens(chatModelConfig.maxTokens().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> streamingChatModel(Langchain4jLocalAiConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = LocalAiStreamingChatModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .modelName(chatModelConfig.modelName())
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP());

        if (chatModelConfig.maxTokens().isPresent()) {
            builder.maxTokens(chatModelConfig.maxTokens().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> embeddingModel(Langchain4jLocalAiConfig runtimeConfig) {
        EmbeddingModelConfig embeddingModelConfig = runtimeConfig.embeddingModel();
        if (embeddingModelConfig.modelName().isEmpty()) {
            throw new ConfigValidationException(createConfigProblems("embedding-model.model-name"));
        }

        var builder = LocalAiEmbeddingModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .modelName(embeddingModelConfig.modelName().get());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> moderationModel(Langchain4jLocalAiConfig runtimeConfig) {
        ModerationModelConfig moderationModelConfig = runtimeConfig.moderationModel();
        if (moderationModelConfig.modelName().isEmpty()) {
            throw new ConfigValidationException(createConfigProblems("moderation-model.model-name"));
        }
        var builder = OpenAiModerationModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .modelName(moderationModelConfig.modelName().get());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key) };
    }

    private ConfigValidationException.Problem createConfigProblem(String key) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.localai.%s is required but it could not be found in any config source",
                key));
    }

    public void cleanUp(ShutdownContext shutdown) {
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                QuarkusOpenAiClient.clearCache();
            }
        });
    }
}
