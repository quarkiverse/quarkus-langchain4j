package io.quarkiverse.langchain4j.azure.openai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import dev.langchain4j.model.image.ImageModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiEmbeddingModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiImageModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig.AzureAiConfig.EndpointType;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.ConfigValidationException.Problem;

@Recorder
public class AzureOpenAiRecorder {

    static final String AZURE_ENDPOINT_URL_PATTERN = "https://%s.%s/openai/deployments/%s";
    public static final Problem[] EMPTY_PROBLEMS = new Problem[0];

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelAuthProvider>> MODEL_AUTH_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public Function<SyntheticCreationalContext<ChatLanguageModel>, ChatLanguageModel> chatModel(
            LangChain4jAzureOpenAiConfig runtimeConfig, String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            var chatModelConfig = azureAiConfig.chatModel();
            var apiKey = firstOrDefault(null, chatModelConfig.apiKey(), azureAiConfig.apiKey());
            var adToken = firstOrDefault(null, chatModelConfig.adToken(), azureAiConfig.adToken());
            var builder = AzureOpenAiChatModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.CHAT))
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName)
                    .apiKey(apiKey)
                    .adToken(adToken)
                    // .tokenizer(new OpenAiTokenizer("<modelName>")) TODO: Set the tokenizer, it is always null!!
                    .apiVersion(chatModelConfig.apiVersion().orElse(azureAiConfig.apiVersion()))
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
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

            return new Function<>() {
                @Override
                public ChatLanguageModel apply(SyntheticCreationalContext<ChatLanguageModel> context) {
                    throwIfApiKeysNotConfigured(apiKey, adToken, isAuthProviderAvailable(context, configName),
                            configName);

                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public ChatLanguageModel apply(SyntheticCreationalContext<ChatLanguageModel> context) {
                    return new DisabledChatLanguageModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<StreamingChatLanguageModel>, StreamingChatLanguageModel> streamingChatModel(
            LangChain4jAzureOpenAiConfig runtimeConfig,
            String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = azureAiConfig.chatModel();
            String apiKey = azureAiConfig.apiKey().orElse(null);
            String adToken = azureAiConfig.adToken().orElse(null);

            var builder = AzureOpenAiStreamingChatModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.CHAT))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName)
                    .apiVersion(azureAiConfig.apiVersion())
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
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

            return new Function<>() {
                @Override
                public StreamingChatLanguageModel apply(SyntheticCreationalContext<StreamingChatLanguageModel> context) {
                    throwIfApiKeysNotConfigured(apiKey, adToken, isAuthProviderAvailable(context, configName),
                            configName);
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public StreamingChatLanguageModel apply(SyntheticCreationalContext<StreamingChatLanguageModel> context) {
                    return new DisabledStreamingChatLanguageModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(
            LangChain4jAzureOpenAiConfig runtimeConfig, String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            var embeddingModelConfig = azureAiConfig.embeddingModel();
            var apiKey = firstOrDefault(null, embeddingModelConfig.apiKey(), azureAiConfig.apiKey());
            var adToken = firstOrDefault(null, embeddingModelConfig.adToken(), azureAiConfig.adToken());
            var builder = AzureOpenAiEmbeddingModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.EMBEDDING))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName)
                    .apiVersion(embeddingModelConfig.apiVersion().orElse(azureAiConfig.apiVersion()))
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(azureAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), azureAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), azureAiConfig.logResponses()));

            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                    throwIfApiKeysNotConfigured(apiKey, adToken, isAuthProviderAvailable(context, configName),
                            configName);
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                    return new DisabledEmbeddingModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<ImageModel>, ImageModel> imageModel(LangChain4jAzureOpenAiConfig runtimeConfig,
            String configName) {
        LangChain4jAzureOpenAiConfig.AzureAiConfig azureAiConfig = correspondingAzureOpenAiConfig(runtimeConfig, configName);

        if (azureAiConfig.enableIntegration()) {
            var imageModelConfig = azureAiConfig.imageModel();
            var apiKey = firstOrDefault(null, imageModelConfig.apiKey(), azureAiConfig.apiKey());
            var adToken = firstOrDefault(null, imageModelConfig.adToken(), azureAiConfig.adToken());
            var builder = AzureOpenAiImageModel.builder()
                    .endpoint(getEndpoint(azureAiConfig, configName, EndpointType.IMAGE))
                    .apiKey(apiKey)
                    .adToken(adToken)
                    .apiVersion(imageModelConfig.apiVersion().orElse(azureAiConfig.apiVersion()))
                    .timeout(azureAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(azureAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, imageModelConfig.logRequests(), azureAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, imageModelConfig.logResponses(), azureAiConfig.logResponses()))
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

            return new Function<>() {
                @Override
                public ImageModel apply(SyntheticCreationalContext<ImageModel> context) {
                    throwIfApiKeysNotConfigured(apiKey, adToken, isAuthProviderAvailable(context, configName),
                            configName);
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public ImageModel apply(SyntheticCreationalContext<ImageModel> context) {
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
        var domainName = azureAiConfig.domainNameFor(type);
        var deploymentName = azureAiConfig.deploymentNameFor(type);

        if (resourceName.isEmpty() || deploymentName.isEmpty() || domainName.isEmpty()) {
            List<Problem> configProblems = new ArrayList<>();

            if (resourceName.isEmpty()) {
                configProblems.add(createConfigProblem("resource-name", configName));
            }

            if (deploymentName.isEmpty()) {
                configProblems.add(createConfigProblem("deployment-name", configName));
            }

            if (domainName.isEmpty()) {
                configProblems.add(createConfigProblem("domain-name", configName));
            }

            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        return String.format(AZURE_ENDPOINT_URL_PATTERN, resourceName.get(), domainName.get(), deploymentName.get());
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

    private void throwIfApiKeysNotConfigured(String apiKey, String adToken, boolean authProviderAvailable, String configName) {
        if ((apiKey != null) == (adToken != null) && !authProviderAvailable) {
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

    private static <T> boolean isAuthProviderAvailable(SyntheticCreationalContext<T> context, String configName) {
        return context.getInjectedReference(MODEL_AUTH_PROVIDER_TYPE_LITERAL).isResolvable();
    }

    public void cleanUp(ShutdownContext shutdown) {
        AdditionalPropertiesHack.reset();
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                QuarkusOpenAiClient.clearCache();
            }
        });
    }

}
