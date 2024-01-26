package io.quarkiverse.langchain4j.azure.openai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.ArrayList;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiEmbeddingModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.Langchain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.ConfigValidationException.Problem;

@Recorder
public class AzureOpenAiRecorder {
    static final String AZURE_ENDPOINT_URL_PATTERN = "https://%s.openai.azure.com/openai/deployments/%s";

    public Supplier<ChatLanguageModel> chatModel(Langchain4jAzureOpenAiConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = AzureOpenAiChatModel.builder()
                .endpoint(getEndpoint(runtimeConfig))
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
            public ChatLanguageModel get() {
                return builder.build();
            }
        };
    }

    public Supplier<StreamingChatLanguageModel> streamingChatModel(Langchain4jAzureOpenAiConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = AzureOpenAiStreamingChatModel.builder()
                .endpoint(getEndpoint(runtimeConfig))
                .apiKey(runtimeConfig.apiKey())
                .apiVersion(runtimeConfig.apiVersion())
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
            public StreamingChatLanguageModel get() {
                return builder.build();
            }
        };
    }

    public Supplier<EmbeddingModel> embeddingModel(Langchain4jAzureOpenAiConfig runtimeConfig) {
        EmbeddingModelConfig embeddingModelConfig = runtimeConfig.embeddingModel();
        var builder = AzureOpenAiEmbeddingModel.builder()
                .endpoint(getEndpoint(runtimeConfig))
                .apiKey(runtimeConfig.apiKey())
                .apiVersion(runtimeConfig.apiVersion())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), runtimeConfig.logResponses()));

        return new Supplier<>() {
            @Override
            public EmbeddingModel get() {
                return builder.build();
            }
        };
    }

    static String getEndpoint(Langchain4jAzureOpenAiConfig runtimeConfig) {
        var endpoint = runtimeConfig.endpoint();

        return (endpoint.isPresent() && !endpoint.get().trim().isBlank()) ? endpoint.get()
                : constructEndpointFromConfig(runtimeConfig);
    }

    private static String constructEndpointFromConfig(Langchain4jAzureOpenAiConfig runtimeConfig) {
        var resourceName = runtimeConfig.resourceName();
        var deploymentName = runtimeConfig.deploymentName();

        if (resourceName.isEmpty() || deploymentName.isEmpty()) {
            var configProblems = new ArrayList<>();

            if (resourceName.isEmpty()) {
                configProblems.add(createConfigProblem("resource-name"));
            }

            if (deploymentName.isEmpty()) {
                configProblems.add(createConfigProblem("deployment-name"));
            }

            throw new ConfigValidationException(configProblems.toArray(new Problem[configProblems.size()]));
        }

        return String.format(AZURE_ENDPOINT_URL_PATTERN, resourceName.get(), deploymentName.get());
    }

    private static ConfigValidationException.Problem createConfigProblem(String key) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.azure-openai.%s is required but it could not be found in any config source",
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
