package io.quarkiverse.langchain4j.azure.openai.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig.AzureAiConfig.EndpointType;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.ConfigValidationException.Problem;

@Recorder
public class AzureOpenAiRecorder {

    static final String AZURE_ENDPOINT_URL_PATTERN = "https://%s.openai.azure.com/openai/deployments/%s";
    public static final Problem[] EMPTY_PROBLEMS = new Problem[0];

    public Supplier<ChatLanguageModel> chatModel(LangChain4jAzureOpenAiConfig runtimeConfig, String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = azureAiConfig.chatModel();
            String apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);

            throwIfApiKeysNotConfigured(apiKey, adToken, configName);

            var builder = AzureOpenAiChatModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.CHAT))
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName)
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(azureAiConfig.maxRetries())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
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
            String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = azureAiConfig.chatModel();
            String apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);

            throwIfApiKeysNotConfigured(apiKey, adToken, configName);

            var builder = AzureOpenAiStreamingChatModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.CHAT))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jAzureOpenAiConfig runtimeConfig, String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            EmbeddingModelConfig embeddingModelConfig = azureAiConfig.embeddingModel();
            String apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);
            if (apiKey == null && adToken == null) {
                throw new ConfigValidationException(createKeyMisconfigurationProblem(configName));
            }
            var builder = AzureOpenAiEmbeddingModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.EMBEDDING))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(azureAiConfig.maxRetries())
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

    public Supplier<ImageModel> imageModel(LangChain4jAzureOpenAiConfig runtimeConfig, String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            var apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);
            throwIfApiKeysNotConfigured(apiKey, adToken, configName);

            var imageModelConfig = azureAiConfig.imageModel();
            var builder = AzureOpenAiImageModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.IMAGE))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(azureAiConfig.maxRetries())
                    .logRequests(imageModelConfig.logRequests().orElse(false))
                    .logResponses(imageModelConfig.logResponses().orElse(false))
                    .modelName(imageModelConfig.modelName())
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName)
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

    static String getEndpoint(LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig, String configName, EndpointType type) {
        var endpoint = azureAiConfig.endPointFor(type);

        return (endpoint.isPresent() && !endpoint.get().trim().isBlank()) ? endpoint.get()
                : constructEndpointFromConfig(azureAiConfig, configName, type);
    }

    private static String constructEndpointFromConfig(
            LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig,
            String configName,
            EndpointType type) {
        var resourceName = azureAiConfig.resourceNameFor(type);
        var deploymentName = azureAiConfig.deploymentNameFor(type);

        if (resourceName.isEmpty() || deploymentName.isEmpty()) {
            List<Problem> configProblems = new ArrayList<>();

            if (resourceName.isEmpty()) {
                configProblems.add(createConfigProblem("resource-name", configName));
            }

            if (deploymentName.isEmpty()) {
                configProblems.add(createConfigProblem("deployment-name", configName));
            }

            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        return String.format(AZURE_ENDPOINT_URL_PATTERN, resourceName.get(), deploymentName.get());
    }

    private LangChain4jAzureOpenAiConfig.AzureAiConfig correspondingAzureOpenAiConfig(
            LangChain4jAzureOpenAiConfig runtimeConfig,
            String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            azureAiConfig = runtimeConfig.defaultConfig();
        } else {
            azureAiConfig = runtimeConfig.namedConfig().get(configName);
        }
        return azureAiConfig;
    }

    private void throwIfApiKeysNotConfigured(String apiKey, String adToken, String configName) {
        if ((apiKey != null) == (adToken != null)) {
            throw new ConfigValidationException(createKeyMisconfigurationProblem(configName));
        }
    }

    private ConfigValidationException.Problem[] createKeyMisconfigurationProblem(String configName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(
                        String.format(
                                "SRCFG00014: Exactly of the configuration properties must be present: quarkus.langchain4j.azure-openai%s%s or quarkus.langchain4j.azure-openai%s%s",
                                io.quarkiverse.langchain4j.runtime.NamedConfigUtil.isDefault(configName) ? "."
                                        : ("." + configName + "."),
                                "api-key",
                                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), "ad-token"))
        };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.azure-openai%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
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
