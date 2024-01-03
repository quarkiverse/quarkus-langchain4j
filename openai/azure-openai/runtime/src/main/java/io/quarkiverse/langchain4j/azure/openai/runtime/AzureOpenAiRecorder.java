package io.quarkiverse.langchain4j.azure.openai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiEmbeddingModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
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
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), runtimeConfig.logResponses()))

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
        var builder = AzureOpenAiStreamingChatModel.builder()
                .baseUrl(getBaseUrl(runtimeConfig))
                .apiKey(runtimeConfig.apiKey())
                .timeout(runtimeConfig.timeout())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), runtimeConfig.logResponses()))

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
        var builder = AzureOpenAiEmbeddingModel.builder()
                .baseUrl(getBaseUrl(runtimeConfig))
                .apiKey(runtimeConfig.apiKey())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), runtimeConfig.logResponses()));

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private String getBaseUrl(Langchain4jAzureOpenAiConfig runtimeConfig) {
        var baseUrl = runtimeConfig.baseUrl();

        return !baseUrl.trim().isEmpty() ? baseUrl
                : String.format("https://%s.openai.azure.com/openai/deployments/%s", runtimeConfig.resourceName(),
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
