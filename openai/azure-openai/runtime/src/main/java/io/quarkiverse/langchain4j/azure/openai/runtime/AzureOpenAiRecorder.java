package io.quarkiverse.langchain4j.azure.openai.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.Langchain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AzureOpenAiRecorder {

    public Supplier<?> chatModel(Langchain4jAzureOpenAiConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = AzureOpenAiChatModel.builder()
                .baseUrl(getBaseUrl(runtimeConfig))
                .apiKey(runtimeConfig.apiKey())
                .apiVersion(runtimeConfig.apiVersion())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .presencePenalty(chatModelConfig.presencePenalty())
                .frequencyPenalty(chatModelConfig.frequencyPenalty());

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

    public Supplier<?> streamingChatModel(Langchain4jAzureOpenAiConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(getBaseUrl(runtimeConfig))
                .apiKey(runtimeConfig.apiKey())
                .timeout(runtimeConfig.timeout())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .presencePenalty(chatModelConfig.presencePenalty())
                .frequencyPenalty(chatModelConfig.frequencyPenalty());

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

    public Supplier<?> embeddingModel(Langchain4jAzureOpenAiConfig runtimeConfig) {
        EmbeddingModelConfig embeddingModelConfig = runtimeConfig.embeddingModel();
        var builder = OpenAiEmbeddingModel.builder()
                .baseUrl(getBaseUrl(runtimeConfig))
                .apiKey(runtimeConfig.apiKey())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .modelName(embeddingModelConfig.modelName());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private String getBaseUrl(Langchain4jAzureOpenAiConfig runtimeConfig) {
        return String.format("https://%s.openai.azure.com/openai/deployments/%s", runtimeConfig.resourceName(),
                runtimeConfig.deploymentId());
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
