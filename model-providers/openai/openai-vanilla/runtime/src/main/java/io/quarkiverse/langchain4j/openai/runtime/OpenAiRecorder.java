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

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

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
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiChatModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiEmbeddingModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiImageModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiModerationModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiStreamingChatModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;
import io.quarkiverse.langchain4j.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ImageModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenAiRecorder {

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private static final String DUMMY_KEY = "dummy";
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1/";

    private final RuntimeValue<LangChain4jOpenAiConfig> runtimeConfig;

    public OpenAiRecorder(RuntimeValue<LangChain4jOpenAiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            ChatModelConfig chatModelConfig = openAiConfig.chatModel();
            var builder = (QuarkusOpenAiChatModelBuilderFactory.Builder) OpenAiChatModel.builder();

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
                    .temperature(chatModelConfig.temperature())
                    .topP(chatModelConfig.topP())
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .responseFormat(chatModelConfig.responseFormat().orElse(null))
                    .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                    .stop(chatModelConfig.stop().orElse(null));

            openAiConfig.organizationId().ifPresent(builder::organizationId);
            openAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(Type.valueOf(openAiConfig.proxyType()),
                        new InetSocketAddress(host, openAiConfig.proxyPort())));
            });

            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().get());
            }

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
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

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            ChatModelConfig chatModelConfig = openAiConfig.chatModel();
            var builder = (QuarkusOpenAiStreamingChatModelBuilderFactory.Builder) OpenAiStreamingChatModel.builder();
            builder
                    .tlsConfigurationName(openAiConfig.tlsConfigurationName().orElse(null))
                    .configName(configName)
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), openAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), openAiConfig.logResponses()))
                    .modelName(chatModelConfig.modelName())
                    .temperature(chatModelConfig.temperature())
                    .topP(chatModelConfig.topP())
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .responseFormat(chatModelConfig.responseFormat().orElse(null))
                    .stop(chatModelConfig.stop().orElse(null));

            openAiConfig.organizationId().ifPresent(builder::organizationId);
            openAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(Type.valueOf(openAiConfig.proxyType()),
                        new InetSocketAddress(host, openAiConfig.proxyPort())));
            });

            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().get());
            }

            return new Function<>() {
                @Override
                public StreamingChatModel apply(
                        SyntheticCreationalContext<StreamingChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
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

    public Supplier<EmbeddingModel> embeddingModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
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
            openAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(Type.valueOf(openAiConfig.proxyType()),
                        new InetSocketAddress(host, openAiConfig.proxyPort())));
            });

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

    public Supplier<ModerationModel> moderationModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            ModerationModelConfig moderationModelConfig = openAiConfig.moderationModel();
            var builder = (QuarkusOpenAiModerationModelBuilderFactory.Builder) OpenAiModerationModel.builder();
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
            openAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(Type.valueOf(openAiConfig.proxyType()),
                        new InetSocketAddress(host, openAiConfig.proxyPort())));
            });

            return new Supplier<>() {
                @Override
                public ModerationModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {

                @Override
                public ModerationModel get() {
                    return new DisabledModerationModel();
                }

            };
        }
    }

    public Supplier<ImageModel> imageModel(String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig.getValue(), configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
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
                    .modelName(imageModelConfig.modelName())
                    .size(imageModelConfig.size())
                    .quality(imageModelConfig.quality())
                    .style(imageModelConfig.style())
                    .responseFormat(imageModelConfig.responseFormat())
                    .user(imageModelConfig.user());

            openAiConfig.organizationId().ifPresent(builder::organizationId);

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

    private ConfigValidationException.Problem[] createApiKeyConfigProblems(String configName) {
        return createConfigProblems("api-key", configName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.openai%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
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
