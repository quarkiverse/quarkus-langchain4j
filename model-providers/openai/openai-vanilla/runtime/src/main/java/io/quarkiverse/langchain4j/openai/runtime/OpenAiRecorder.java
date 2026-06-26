package io.quarkiverse.langchain4j.openai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.openai.DisabledAudioTranscriptionModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiAudioTranscriptionModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiChatModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiEmbeddingModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiImageModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiModerationModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiStreamingChatModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;
import io.quarkiverse.langchain4j.openai.runtime.config.AudioTranscriptionModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ImageModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.proxy.ProxyConfiguration;
import io.quarkus.proxy.ProxyConfigurationRegistry;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenAiRecorder {

    private static final Logger log = Logger.getLogger(OpenAiRecorder.class);

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder>>> EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OpenAiModerationModel.OpenAiModerationModelBuilder>>> MODERATION_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<QuarkusOpenAiImageModel.Builder>>> IMAGE_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OpenAiAudioTranscriptionModel.Builder>>> AUDIO_TRANSCRIPTION_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelAuthProvider>> MODEL_AUTH_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private static final String DUMMY_KEY = "dummy";
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1/";

    private final RuntimeValue<LangChain4jOpenAiConfig> runtimeConfig;

    public OpenAiRecorder(RuntimeValue<LangChain4jOpenAiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @SuppressWarnings("deprecation")
    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (openAiConfig.maxRetries() < 1) {
                throw new ConfigValidationException(createMaxRetriesConfigProblems(configName));
            }
            ChatModelConfig chatModelConfig = openAiConfig.chatModel();
            var builder = (QuarkusOpenAiChatModelBuilderFactory.Builder) OpenAiChatModel.builder();
            builder.logCurl(firstOrDefault(false, openAiConfig.logRequestsCurl()));

            OpenAiChatRequestParameters.Builder defaultChatRequestParametersBuilder = OpenAiChatRequestParameters.builder();
            if (chatModelConfig.reasoningEffort().isPresent()) {
                defaultChatRequestParametersBuilder.reasoningEffort(chatModelConfig.reasoningEffort().get());
            }

            builder
                    .tlsConfigurationName(openAiConfig.tlsConfigurationName().orElse(null))
                    .configName(configName)
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), openAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), openAiConfig.logResponses()))
                    .modelName(chatModelConfig.modelName())
                    .temperature(chatModelConfig.temperature().orElse(null))
                    .topP(chatModelConfig.topP().orElse(null))
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .responseFormat(chatModelConfig.responseFormat().orElse(null))
                    .defaultRequestParameters(defaultChatRequestParametersBuilder.build())
                    .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                    .serviceTier(chatModelConfig.serviceTier().orElse(null))
                    .stop(chatModelConfig.stop().orElse(null));

            openAiConfig.organizationId().ifPresent(builder::organizationId);

            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().get());
            }
            if (chatModelConfig.maxCompletionTokens().isPresent()) {
                builder.maxCompletionTokens(chatModelConfig.maxCompletionTokens().get());
            }

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    throwIfApiKeyNotConfigured(apiKey, openAiConfig.baseUrl(), context, configName);
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));

                    ProxyConfigurationRegistry proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    Optional<Proxy> chatProxy = resolveProxy(openAiConfig, proxyRegistry);
                    if (chatProxy.isPresent()) {
                        builder.proxy(chatProxy.get());
                    }

                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);

                    return builder.build();
                }
            };
        } else {
            return new Function<>() {

                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    return new DisabledChatModel();
                }

            };
        }
    }

    @SuppressWarnings("deprecation")
    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            ChatModelConfig chatModelConfig = openAiConfig.chatModel();
            var builder = (QuarkusOpenAiStreamingChatModelBuilderFactory.Builder) OpenAiStreamingChatModel.builder();
            builder.logCurl(firstOrDefault(false, openAiConfig.logRequestsCurl()));
            builder
                    .tlsConfigurationName(openAiConfig.tlsConfigurationName().orElse(null))
                    .configName(configName)
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), openAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), openAiConfig.logResponses()))
                    .modelName(chatModelConfig.modelName())
                    .temperature(chatModelConfig.temperature().orElse(null))
                    .topP(chatModelConfig.topP().orElse(null))
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .responseFormat(chatModelConfig.responseFormat().orElse(null))
                    .stop(chatModelConfig.stop().orElse(null));

            openAiConfig.organizationId().ifPresent(builder::organizationId);

            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().get());
            }
            if (chatModelConfig.maxCompletionTokens().isPresent()) {
                builder.maxCompletionTokens(chatModelConfig.maxCompletionTokens().get());
            }

            return new Function<>() {
                @Override
                public StreamingChatModel apply(
                        SyntheticCreationalContext<StreamingChatModel> context) {
                    throwIfApiKeyNotConfigured(apiKey, openAiConfig.baseUrl(), context, configName);
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));

                    ProxyConfigurationRegistry proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    Optional<Proxy> streamingProxy = resolveProxy(openAiConfig, proxyRegistry);
                    if (streamingProxy.isPresent()) {
                        builder.proxy(streamingProxy.get());
                    }

                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public StreamingChatModel apply(
                        SyntheticCreationalContext<StreamingChatModel> streamingChatLanguageModelSyntheticCreationalContext) {
                    return new DisabledStreamingChatModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (openAiConfig.maxRetries() < 1) {
                throw new ConfigValidationException(createMaxRetriesConfigProblems(configName));
            }
            EmbeddingModelConfig embeddingModelConfig = openAiConfig.embeddingModel();
            var builder = (QuarkusOpenAiEmbeddingModelBuilderFactory.Builder) OpenAiEmbeddingModel.builder();
            builder
                    .tlsConfigurationName(openAiConfig.tlsConfigurationName().orElse(null))
                    .configName(configName)
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), openAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), openAiConfig.logResponses()))
                    .modelName(embeddingModelConfig.modelName());

            if (embeddingModelConfig.user().isPresent()) {
                builder.user(embeddingModelConfig.user().get());
            }

            openAiConfig.organizationId().ifPresent(builder::organizationId);

            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                    throwIfApiKeyNotConfigured(apiKey, openAiConfig.baseUrl(), context, configName);
                    ProxyConfigurationRegistry proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    Optional<Proxy> embeddingProxy = resolveProxy(openAiConfig, proxyRegistry);
                    if (embeddingProxy.isPresent()) {
                        builder.proxy(embeddingProxy.get());
                    }

                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);

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

    public Function<SyntheticCreationalContext<ModerationModel>, ModerationModel> moderationModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (openAiConfig.maxRetries() < 1) {
                throw new ConfigValidationException(createMaxRetriesConfigProblems(configName));
            }
            ModerationModelConfig moderationModelConfig = openAiConfig.moderationModel();
            var builder = (QuarkusOpenAiModerationModelBuilderFactory.Builder) OpenAiModerationModel.builder();
            builder.logCurl(firstOrDefault(false, openAiConfig.logRequestsCurl()));
            builder
                    .tlsConfigurationName(openAiConfig.tlsConfigurationName().orElse(null))
                    .configName(configName)
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, moderationModelConfig.logRequests(), openAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, moderationModelConfig.logResponses(), openAiConfig.logResponses()))
                    .modelName(moderationModelConfig.modelName());

            openAiConfig.organizationId().ifPresent(builder::organizationId);

            return new Function<>() {
                @Override
                public ModerationModel apply(SyntheticCreationalContext<ModerationModel> context) {
                    throwIfApiKeyNotConfigured(apiKey, openAiConfig.baseUrl(), context, configName);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(MODERATION_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    ProxyConfigurationRegistry proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    Optional<Proxy> moderationProxy = resolveProxy(openAiConfig, proxyRegistry);
                    if (moderationProxy.isPresent()) {
                        builder.proxy(moderationProxy.get());
                    }
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public ModerationModel apply(SyntheticCreationalContext<ModerationModel> context) {
                    return new DisabledModerationModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<ImageModel>, ImageModel> imageModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            ImageModelConfig imageModelConfig = openAiConfig.imageModel();
            var builder = QuarkusOpenAiImageModel.builder()
                    .configName(configName)
                    .tlsConfigurationName(openAiConfig.tlsConfigurationName().orElse(null))
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(firstOrDefault(false, imageModelConfig.logRequests(), openAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, imageModelConfig.logResponses(), openAiConfig.logResponses()))
                    .logCurl(firstOrDefault(false, openAiConfig.logRequestsCurl()))
                    .modelName(imageModelConfig.modelName())
                    .size(imageModelConfig.size())
                    .quality(imageModelConfig.quality())
                    .user(imageModelConfig.user());

            imageModelConfig.outputFormat().ifPresent(builder::outputFormat);
            imageModelConfig.background().ifPresent(builder::background);
            imageModelConfig.outputCompression().ifPresent(builder::outputCompression);
            imageModelConfig.moderation().ifPresent(builder::moderation);

            openAiConfig.organizationId().ifPresent(builder::organizationId);

            // we persist if the directory was set explicitly and the boolean flag was not set to false
            // or if the boolean flag was set explicitly to true
            Optional<Path> persistDirectory = Optional.empty();
            if (imageModelConfig.persist().isPresent()) {
                if (imageModelConfig.persist().get()) {
                    persistDirectory = imageModelConfig.persistDirectory().or(new Supplier<>() {
                        @Override
                        public Optional<? extends Path> get() {
                            return Optional.of(Paths.get(System.getProperty("java.io.tmpdir"), "openai-images"));
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
                    throwIfApiKeyNotConfigured(apiKey, openAiConfig.baseUrl(), context, configName);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(IMAGE_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    ProxyConfigurationRegistry proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    Optional<Proxy> imageProxy = resolveProxy(openAiConfig, proxyRegistry);
                    if (imageProxy.isPresent()) {
                        builder.proxy(imageProxy.get());
                    }
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

    public Function<SyntheticCreationalContext<AudioTranscriptionModel>, AudioTranscriptionModel> audioTranscriptionModel(
            String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (openAiConfig.maxRetries() < 1) {
                throw new ConfigValidationException(createMaxRetriesConfigProblems(configName));
            }
            AudioTranscriptionModelConfig audioTranscriptionModelConfig = openAiConfig.audioTranscriptionModel();
            var builder = (QuarkusOpenAiAudioTranscriptionModelBuilderFactory.Builder) OpenAiAudioTranscriptionModel.builder();
            builder
                    .tlsConfigurationName(openAiConfig.tlsConfigurationName().orElse(null))
                    .configName(configName)
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(
                            firstOrDefault(false, audioTranscriptionModelConfig.logRequests(), openAiConfig.logRequests()))
                    .logResponses(
                            firstOrDefault(false, audioTranscriptionModelConfig.logResponses(), openAiConfig.logResponses()))
                    .modelName(audioTranscriptionModelConfig.modelName());

            openAiConfig.organizationId().ifPresent(builder::organizationId);

            return new Function<>() {
                @Override
                public AudioTranscriptionModel apply(SyntheticCreationalContext<AudioTranscriptionModel> context) {
                    throwIfApiKeyNotConfigured(apiKey, openAiConfig.baseUrl(), context, configName);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(AUDIO_TRANSCRIPTION_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                    Any.Literal.INSTANCE),
                            builder, configName);
                    ProxyConfigurationRegistry proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    Optional<Proxy> audioProxy = resolveProxy(openAiConfig, proxyRegistry);
                    if (audioProxy.isPresent()) {
                        builder.proxy(audioProxy.get());
                    }
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public AudioTranscriptionModel apply(SyntheticCreationalContext<AudioTranscriptionModel> context) {
                    return new DisabledAudioTranscriptionModel();
                }
            };
        }
    }

    @SuppressWarnings({ "removal" })
    private Optional<Proxy> resolveProxy(LangChain4jOpenAiConfig.OpenAiConfig openAiConfig,
            ProxyConfigurationRegistry proxyRegistry) {

        if (openAiConfig.proxyConfigurationName().isPresent()) {
            String configName = openAiConfig.proxyConfigurationName().get();

            if (openAiConfig.proxyHost().isPresent()) {
                log.warnf("Both 'proxy-configuration-name' (%s) and deprecated 'proxy-host' (%s) are set. " +
                        "The 'proxy-host' configuration will be ignored. " +
                        "Please remove 'proxy-host' and 'proxy-port' from your configuration.",
                        configName, openAiConfig.proxyHost().get());
            }

            return proxyRegistry.get(openAiConfig.proxyConfigurationName())
                    .map(new Function<ProxyConfiguration, Proxy>() {
                        @Override
                        public Proxy apply(ProxyConfiguration pc) {
                            return new Proxy(
                                    Type.valueOf(pc.type().name()),
                                    new InetSocketAddress(pc.host(), pc.port()));
                        }
                    });
        }

        if (openAiConfig.proxyHost().isPresent()) {
            log.warnf("Using deprecated 'proxy-host' configuration. " +
                    "Please migrate to 'proxy-configuration-name' using Quarkus Proxy Registry. " +
                    "The 'proxy-host', 'proxy-port', and 'proxy-type' properties will be removed in a future version.");

            return Optional.of(new Proxy(
                    Type.valueOf(openAiConfig.proxyType()),
                    new InetSocketAddress(openAiConfig.proxyHost().get(), openAiConfig.proxyPort())));
        }

        return proxyRegistry.get(Optional.empty())
                .map(new Function<ProxyConfiguration, Proxy>() {
                    @Override
                    public Proxy apply(ProxyConfiguration pc) {
                        return new Proxy(
                                Type.valueOf(pc.type().name()),
                                new InetSocketAddress(pc.host(), pc.port()));
                    }
                });
    }

    private LangChain4jOpenAiConfig.OpenAiConfig correspondingOpenAiConfig(LangChain4jOpenAiConfig runtimeConfig,
            String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            openAiConfig = runtimeConfig.defaultConfig();
        } else {
            openAiConfig = runtimeConfig.namedConfig().get(configName);
        }
        return openAiConfig;
    }

    private static ConfigValidationException.Problem[] createApiKeyConfigProblems(String configName) {
        return createConfigProblems("api-key", configName);
    }

    private static ConfigValidationException.Problem[] createMaxRetriesConfigProblems(String configName) {
        return new ConfigValidationException.Problem[] { new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.openai%smax-retries must be greater than zero",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."))) };
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.openai%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }

    private static <T> void throwIfApiKeyNotConfigured(String apiKey, String baseUrl,
            SyntheticCreationalContext<T> context, String configName) {
        if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(baseUrl) && !isAuthProviderAvailable(context, configName)) {
            throw new ConfigValidationException(createApiKeyConfigProblems(configName));
        }
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
