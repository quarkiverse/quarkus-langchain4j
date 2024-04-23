package io.quarkiverse.langchain4j.azure.openai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import dev.langchain4j.model.image.ImageModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiEmbeddingModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiImageModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.ConfigValidationException.Problem;

@Recorder
public class AzureOpenAiRecorder {

    static final String AZURE_ENDPOINT_URL_PATTERN = "https://%s.openai.azure.com/openai/deployments/%s";
    public static final Problem[] EMPTY_PROBLEMS = new Problem[0];

    public Supplier<ChatLanguageModel> chatModel(LangChain4jAzureOpenAiConfig runtimeConfig, String modelName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, modelName);

        if (azureAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = azureAiConfig.chatModel();
            String apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);

            throwIfApiKeysNotConfigured(apiKey, adToken, modelName);

            var builder = AzureOpenAiChatModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, modelName))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout())
                    .maxRetries(azureAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), azureAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), azureAiConfig.logResponses()))
                    .temperature(chatModelConfig.temperature())
                    .topP(chatModelConfig.topP())
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .responseFormat(chatModelConfig.responseFormat().orElse(null));

            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().get());
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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jAzureOpenAiConfig runtimeConfig,
            String modelName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, modelName);

        if (azureAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = azureAiConfig.chatModel();
            String apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);

            throwIfApiKeysNotConfigured(apiKey, adToken, modelName);

            var builder = AzureOpenAiStreamingChatModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, modelName))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), azureAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), azureAiConfig.logResponses()))
                    .temperature(chatModelConfig.temperature())
                    .topP(chatModelConfig.topP())
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .responseFormat(chatModelConfig.responseFormat().orElse(null));

            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().get());
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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jAzureOpenAiConfig runtimeConfig, String modelName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, modelName);

        if (azureAiConfig.enableIntegration()) {
            EmbeddingModelConfig embeddingModelConfig = azureAiConfig.embeddingModel();
            String apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);
            if (apiKey == null && adToken == null) {
                throw new ConfigValidationException(createKeyMisconfigurationProblem(modelName));
            }
            var builder = AzureOpenAiEmbeddingModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, modelName))
                    .apiKey(apiKey)
                    .adToken(apiKey)
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
        } else {
            return new Supplier<>() {
                @Override
                public EmbeddingModel get() {
                    return new DisabledEmbeddingModel();
                }
            };
        }
    }

    public Supplier<ImageModel> imageModel(LangChain4jAzureOpenAiConfig runtimeConfig, String modelName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, modelName);

        if (azureAiConfig.enableIntegration()) {
            var apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);
            throwIfApiKeysNotConfigured(apiKey, adToken, modelName);

            var imageModelConfig = azureAiConfig.imageModel();
            var builder = AzureOpenAiImageModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, modelName))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout())
                    .maxRetries(azureAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, imageModelConfig.logRequests(), azureAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, imageModelConfig.logResponses(), azureAiConfig.logResponses()))
                    .modelName(imageModelConfig.modelName())
                    .size(imageModelConfig.size())
                    .quality(imageModelConfig.quality())
                    .style(imageModelConfig.style())
                    .responseFormat(imageModelConfig.responseFormat())
                    .user(imageModelConfig.user());

            // we persist if the directory was set explicitly and the boolean flag was not set to false
            // or if the boolean flag was set explicitly to true
            Optional<Path> persistDirectory = Optional.empty();
            if (imageModelConfig.persist().isPresent()) {
                if (imageModelConfig.persist().get()) {
                    persistDirectory = imageModelConfig.persistDirectory().or(new Supplier<>() {
                        @Override
                        public Optional<? extends Path> get() {
                            return Optional.of(Paths.get(System.getProperty("java.io.tmpdir"), "dall-e-images"));
                        }
                    });
                }
            } else {
                if (imageModelConfig.persistDirectory().isPresent()) {
                    persistDirectory = imageModelConfig.persistDirectory();
                }
            }

            builder.persistDirectory(persistDirectory);

            return new Supplier<>() {
                @Override
                public ImageModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public ImageModel get() {
                    return new DisabledImageModel();
                }
            };
        }
    }

    static String getEndpoint(LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig, String modelName) {
        var endpoint = azureAiConfig.endpoint();

        return (endpoint.isPresent() && !endpoint.get().trim().isBlank()) ? endpoint.get()
                : constructEndpointFromConfig(azureAiConfig, modelName);
    }

    private static String constructEndpointFromConfig(LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig,
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

    private LangChain4jAzureOpenAiConfig.AzureAiConfig correspondingAzureOpenAiConfig(
            LangChain4jAzureOpenAiConfig runtimeConfig,
            String modelName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            azureAiConfig = runtimeConfig.defaultConfig();
        } else {
            azureAiConfig = runtimeConfig.namedConfig().get(modelName);
        }
        return azureAiConfig;
    }

    private void throwIfApiKeysNotConfigured(String apiKey, String adToken, String modelName) {
        if ((apiKey != null) == (adToken != null)) {
            throw new ConfigValidationException(createKeyMisconfigurationProblem(modelName));
        }
    }

    private ConfigValidationException.Problem[] createKeyMisconfigurationProblem(String modelName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(
                        String.format(
                                "SRCFG00014: Exactly of the configuration properties must be present: quarkus.langchain4j.azure-openai%s%s or quarkus.langchain4j.azure-openai%s%s",
                                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), "api-key",
                                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), "ad-token"))
        };
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
