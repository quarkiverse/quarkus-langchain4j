package io.quarkiverse.langchain4j.azure.openai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.ArrayList;
import java.util.List;
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
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.ConfigValidationException.Problem;

@Recorder
public class AzureOpenAiRecorder {

    private static final String DUMMY_KEY = "dummy";
    static final String AZURE_ENDPOINT_URL_PATTERN = "https://%s.openai.azure.com/openai/deployments/%s";
    public static final Problem[] EMPTY_PROBLEMS = new Problem[0];

    public Supplier<ChatLanguageModel> chatModel(Langchain4jAzureOpenAiConfig runtimeConfig, String modelName) {
        Langchain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, modelName);
        ChatModelConfig chatModelConfig = azureAiConfig.chatModel();
        String apiKey = azureAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
        }
        var builder = AzureOpenAiChatModel.builder()
                .endpoint(getEndpoint(azureAiConfig, modelName))
                .apiKey(apiKey)
                .apiVersion(azureAiConfig.apiVersion())
                .timeout(azureAiConfig.timeout())
                .maxRetries(azureAiConfig.maxRetries())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), azureAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), azureAiConfig.logResponses()))

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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(Langchain4jAzureOpenAiConfig runtimeConfig,
            String modelName) {
        Langchain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, modelName);
        ChatModelConfig chatModelConfig = azureAiConfig.chatModel();
        String apiKey = azureAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
        }
        var builder = AzureOpenAiStreamingChatModel.builder()
                .endpoint(getEndpoint(azureAiConfig, modelName))
                .apiKey(apiKey)
                .apiVersion(azureAiConfig.apiVersion())
                .timeout(azureAiConfig.timeout())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), azureAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), azureAiConfig.logResponses()))

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

    public Supplier<EmbeddingModel> embeddingModel(Langchain4jAzureOpenAiConfig runtimeConfig, String modelName) {
        Langchain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, modelName);
        EmbeddingModelConfig embeddingModelConfig = azureAiConfig.embeddingModel();
        String apiKey = azureAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
        }
        var builder = AzureOpenAiEmbeddingModel.builder()
                .endpoint(getEndpoint(azureAiConfig, modelName))
                .apiKey(apiKey)
                .apiVersion(azureAiConfig.apiVersion())
                .timeout(azureAiConfig.timeout())
                .maxRetries(azureAiConfig.maxRetries())
                .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), azureAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), azureAiConfig.logResponses()));

        return new Supplier<>() {
            @Override
            public EmbeddingModel get() {
                return builder.build();
            }
        };
    }

    static String getEndpoint(Langchain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig, String modelName) {
        var endpoint = azureAiConfig.endpoint();

        return (endpoint.isPresent() && !endpoint.get().trim().isBlank()) ? endpoint.get()
                : constructEndpointFromConfig(azureAiConfig, modelName);
    }

    private static String constructEndpointFromConfig(Langchain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig,
            String modelName) {
        var resourceName = azureAiConfig.resourceName();
        var deploymentName = azureAiConfig.deploymentName();

        if (resourceName.isEmpty() || deploymentName.isEmpty()) {
            List<Problem> configProblems = new ArrayList<>();

            if (resourceName.isEmpty()) {
                configProblems.add(createConfigProblem("resource-name", modelName));
            }

            if (deploymentName.isEmpty()) {
                configProblems.add(createConfigProblem("deployment-name", modelName));
            }

            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        return String.format(AZURE_ENDPOINT_URL_PATTERN, resourceName.get(), deploymentName.get());
    }

    private Langchain4jAzureOpenAiConfig.AzureAiConfig correspondingAzureOpenAiConfig(
            Langchain4jAzureOpenAiConfig runtimeConfig,
            String modelName) {
        Langchain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            azureAiConfig = runtimeConfig.defaultConfig();
        } else {
            azureAiConfig = runtimeConfig.namedConfig().get(modelName);
        }
        return azureAiConfig;
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblem(String modelName) {
        return createConfigProblems("api-key", modelName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String modelName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, modelName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.azure-openai%s%s is required but it could not be found in any config source",
                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
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
