package io.quarkiverse.langchain4j.google.genai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.eclipse.microprofile.context.ManagedExecutor;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiStreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.google.genai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.google.genai.runtime.config.LangChain4jGoogleGenAiConfig;
import io.quarkiverse.langchain4j.google.genai.runtime.config.LangChain4jGoogleGenAiConfig.GoogleGenAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.proxy.ProxyConfiguration;
import io.quarkus.proxy.ProxyConfigurationRegistry;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.RuntimeInit;
import io.smallrye.config.ConfigValidationException;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

@Recorder
public class GoogleGenAiRecorder {

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<GoogleGenAiChatModel.Builder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<GoogleGenAiStreamingChatModel.Builder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<GoogleGenAiEmbeddingModel.Builder>>> EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jGoogleGenAiConfig> runtimeConfig;

    public GoogleGenAiRecorder(RuntimeValue<LangChain4jGoogleGenAiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @RuntimeInit
    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName,
            boolean openTelemetryAvailable) {
        var config = correspondingConfig(configName);

        if (config.enableIntegration()) {
            var chatModelConfig = config.chatModel();

            String apiKey = config.apiKey().orElse(null);
            throwIfApiKeyNotConfigured(apiKey, configName);

            var builder = GoogleGenAiChatModel.builder()
                    .modelName(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), config.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), config.logResponses()));

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().get());
            }
            if (chatModelConfig.topK().isPresent()) {
                builder.topK(chatModelConfig.topK().getAsInt());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }

            if (config.timeout().isPresent()) {
                builder.timeout(config.timeout().get());
            }

            configureThinking(chatModelConfig, builder);

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {

                    var managedExecutor = context.getInjectedReference(ManagedExecutor.class);
                    var proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    builder.client(createClient(config, managedExecutor, openTelemetryAvailable, apiKey, proxyRegistry));
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));

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

    @RuntimeInit
    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName,
            boolean openTelemetryAvailable) {
        var config = correspondingConfig(configName);

        if (config.enableIntegration()) {
            var chatModelConfig = config.chatModel();

            String apiKey = config.apiKey().orElse(null);
            throwIfApiKeyNotConfigured(apiKey, configName);

            var builder = GoogleGenAiStreamingChatModel.builder()
                    .modelName(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), config.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), config.logResponses()));

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().get());
            }
            if (chatModelConfig.topK().isPresent()) {
                builder.topK(chatModelConfig.topK().getAsInt());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }

            if (config.timeout().isPresent()) {
                builder.timeout(config.timeout().get());
            }

            configureThinking(chatModelConfig, builder);

            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {

                    var managedExecutor = context.getInjectedReference(ManagedExecutor.class);
                    var proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    builder.client(createClient(config, managedExecutor, openTelemetryAvailable, apiKey, proxyRegistry));
                    builder.executor(managedExecutor);
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));

                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                    Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    return new DisabledStreamingChatModel();
                }
            };
        }
    }

    @RuntimeInit
    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName,
            boolean openTelemetryAvailable) {
        var config = correspondingConfig(configName);

        if (config.enableIntegration()) {
            var embeddingModelConfig = config.embeddingModel();

            String apiKey = config.apiKey().orElse(null);
            throwIfApiKeyNotConfigured(apiKey, configName);

            var builder = GoogleGenAiEmbeddingModel.builder()
                    .modelName(embeddingModelConfig.modelId())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), config.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), config.logResponses()));

            if (embeddingModelConfig.outputDimension().isPresent()) {
                builder.outputDimensionality(embeddingModelConfig.outputDimension().get());
            }

            if (embeddingModelConfig.taskType().isPresent()) {
                builder.taskType(
                        GoogleGenAiEmbeddingModel.TaskTypeEnum.valueOf(embeddingModelConfig.taskType().get()));
            }

            if (config.timeout().isPresent()) {
                builder.timeout(config.timeout().get());
            }

            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {

                    var managedExecutor = context.getInjectedReference(ManagedExecutor.class);
                    var proxyRegistry = context.getInjectedReference(ProxyConfigurationRegistry.class);
                    builder.client(createClient(config, managedExecutor, openTelemetryAvailable, apiKey, proxyRegistry));

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

    private Client createClient(GoogleGenAiConfig config, ManagedExecutor managedExecutor,
            boolean openTelemetryAvailable, String apiKey, ProxyConfigurationRegistry proxyRegistry) {
        Dispatcher dispatcher = new Dispatcher(managedExecutor);
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder()
                .dispatcher(dispatcher);

        if (openTelemetryAvailable) {
            okHttpBuilder.addInterceptor(new OkHttpOpenTelemetryInterceptor());
        }

        Optional<Proxy> proxy = resolveProxy(config, proxyRegistry);
        if (proxy.isPresent()) {
            okHttpBuilder.proxy(proxy.get());
        }

        OkHttpClient okHttpClient = okHttpBuilder.build();

        ClientOptions clientOptions = ClientOptions.builder()
                .customHttpClient(okHttpClient)
                .build();

        HttpOptions.Builder httpOptions = HttpOptions.builder();
        if (config.timeout().isPresent()) {
            httpOptions.timeout((int) config.timeout().get().toMillis());
        }
        if (config.baseUrl().isPresent()) {
            httpOptions.baseUrl(config.baseUrl().get());
        }

        Client.Builder clientBuilder = Client.builder()
                .httpOptions(httpOptions.build())
                .clientOptions(clientOptions);

        clientBuilder.apiKey(apiKey);

        return clientBuilder.build();
    }

    private Optional<Proxy> resolveProxy(GoogleGenAiConfig config, ProxyConfigurationRegistry proxyRegistry) {
        Optional<ProxyConfiguration> proxyConfig;
        if (config.proxyConfigurationName().isPresent()) {
            proxyConfig = proxyRegistry.get(config.proxyConfigurationName());
        } else {
            proxyConfig = proxyRegistry.get(Optional.empty());
        }
        if (proxyConfig.isPresent()) {
            ProxyConfiguration pc = proxyConfig.get();
            return Optional.of(new Proxy(Proxy.Type.valueOf(pc.type().name()),
                    new InetSocketAddress(pc.host(), pc.port())));
        }
        return Optional.empty();
    }

    private void configureThinking(ChatModelConfig chatModelConfig, GoogleGenAiChatModel.Builder builder) {
        if (chatModelConfig.thinking().thinkingBudget().isPresent()) {
            builder.thinkingBudget(chatModelConfig.thinking().thinkingBudget().getAsInt());
        }
        if (chatModelConfig.thinking().thinkingLevel().isPresent()) {
            builder.thinkingLevel(chatModelConfig.thinking().thinkingLevel().get());
        }
    }

    private void configureThinking(ChatModelConfig chatModelConfig, GoogleGenAiStreamingChatModel.Builder builder) {
        if (chatModelConfig.thinking().thinkingBudget().isPresent()) {
            builder.thinkingBudget(chatModelConfig.thinking().thinkingBudget().getAsInt());
        }
        if (chatModelConfig.thinking().thinkingLevel().isPresent()) {
            builder.thinkingLevel(chatModelConfig.thinking().thinkingLevel().get());
        }
    }

    private void throwIfApiKeyNotConfigured(String apiKey, String configName) {
        if (apiKey == null) {
            throw new ConfigValidationException(createConfigProblems("api-key", configName));
        }
    }

    private GoogleGenAiConfig correspondingConfig(String configName) {
        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.google.genai%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
