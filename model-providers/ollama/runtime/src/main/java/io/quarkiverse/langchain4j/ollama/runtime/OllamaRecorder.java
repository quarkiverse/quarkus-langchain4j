package io.quarkiverse.langchain4j.ollama.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel;
import io.quarkiverse.langchain4j.ollama.OllamaModelAuthProviderFilter;
import io.quarkiverse.langchain4j.ollama.OllamaStreamingChatLanguageModel;
import io.quarkiverse.langchain4j.ollama.Options;
import io.quarkiverse.langchain4j.ollama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaFixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;

@Recorder
public class OllamaRecorder {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final LangChain4jOllamaFixedRuntimeConfig fixedRuntimeConfig;
    private final RuntimeValue<LangChain4jOllamaConfig> runtimeConfig;

    public OllamaRecorder(LangChain4jOllamaFixedRuntimeConfig fixedRuntimeConfig,
            RuntimeValue<LangChain4jOllamaConfig> runtimeConfig) {
        this.fixedRuntimeConfig = fixedRuntimeConfig;
        this.runtimeConfig = runtimeConfig;
    }

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OllamaChatModel.OllamaChatModelBuilder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OllamaStreamingChatLanguageModel.Builder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<OllamaEmbeddingModel.Builder>>> EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig.getValue(), configName);
        LangChain4jOllamaFixedRuntimeConfig.OllamaConfig ollamaFixedConfig = correspondingOllamaFixedConfig(fixedRuntimeConfig,
                configName);

        if (ollamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = ollamaConfig.chatModel();

            JaxRsHttpClientBuilder httpClientBuilder = new JaxRsHttpClientBuilder();

            OllamaChatModel.OllamaChatModelBuilder ollamaChatModelBuilder = OllamaChatModel.builder()
                    .httpClientBuilder(httpClientBuilder)
                    .baseUrl(ollamaConfig.baseUrl().orElse(DEFAULT_BASE_URL))
                    .timeout(ollamaConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), ollamaConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), ollamaConfig.logResponses()))
                    .modelName(ollamaFixedConfig.chatModel().modelId())
                    .responseFormat(chatModelConfig.format().filter("json"::equalsIgnoreCase).map(format -> ResponseFormat.JSON)
                            .orElse(null))
                    .temperature(chatModelConfig.temperature())
                    .topK(chatModelConfig.topK())
                    .topP(chatModelConfig.topP());
            if (chatModelConfig.numPredict().isPresent()) {
                ollamaChatModelBuilder.numPredict(chatModelConfig.numPredict().getAsInt());
            }
            if (chatModelConfig.stop().isPresent()) {
                ollamaChatModelBuilder.stop(chatModelConfig.stop().get());
            }
            if (chatModelConfig.seed().isPresent()) {
                ollamaChatModelBuilder.seed(chatModelConfig.seed().get());
            }
            if (chatModelConfig.format().isEmpty() || !"json".equals(chatModelConfig.format().get())) {
                ollamaChatModelBuilder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
            }

            ChatModelConfig.ModelOptionsConfig modelOptions = chatModelConfig.modelOptions();
            if (modelOptions.think().isPresent()) {
                ollamaChatModelBuilder.think(modelOptions.think().get());
            }
            if (modelOptions.returnThinking().isPresent()) {
                ollamaChatModelBuilder.returnThinking(modelOptions.returnThinking().get());
            }
            if (modelOptions.numCtx().isPresent()) {
                ollamaChatModelBuilder.numCtx(modelOptions.numCtx().getAsInt());
            }
            if (modelOptions.repeatLastN().isPresent()) {
                ollamaChatModelBuilder.repeatLastN(modelOptions.repeatLastN().getAsInt());
            }
            if (modelOptions.repeatPenalty().isPresent()) {
                ollamaChatModelBuilder.repeatPenalty(modelOptions.repeatPenalty().getAsDouble());
            }
            if (modelOptions.mirostat().isPresent()) {
                ollamaChatModelBuilder.mirostat(modelOptions.mirostat().getAsInt());
            }
            if (modelOptions.mirostatEta().isPresent()) {
                ollamaChatModelBuilder.mirostatEta(modelOptions.mirostatEta().getAsDouble());
            }
            if (modelOptions.mirostatTau().isPresent()) {
                ollamaChatModelBuilder.mirostatTau(modelOptions.mirostatTau().getAsDouble());
            }
            if (modelOptions.minP().isPresent()) {
                ollamaChatModelBuilder.minP(modelOptions.minP().getAsDouble());
            }

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    ollamaChatModelBuilder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            ollamaChatModelBuilder, configName);

                    // TODO: we should obtain this from the context
                    Optional<ModelAuthProvider> maybeModelAuthProvider = ModelAuthProvider
                            .resolve(NamedConfigUtil.isDefault(configName) ? null : configName);
                    if (maybeModelAuthProvider.isPresent()) {
                        httpClientBuilder.addClientProvider(new OllamaModelAuthProviderFilter(maybeModelAuthProvider.get()));
                    }

                    // TODO: we should obtain this from the context
                    Instance<TlsConfigurationRegistry> tlsConfigurationRegistry = CDI.current()
                            .select(TlsConfigurationRegistry.class);
                    if (tlsConfigurationRegistry.isResolvable()) {
                        Optional<TlsConfiguration> maybeTlsConfiguration = TlsConfiguration.from(tlsConfigurationRegistry.get(),
                                ollamaConfig.tlsConfigurationName());
                        if (maybeTlsConfiguration.isPresent()) {
                            httpClientBuilder.tlsConfiguration(maybeTlsConfiguration.get());
                        }
                    }

                    return ollamaChatModelBuilder.build();
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

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig.getValue(), configName);
        LangChain4jOllamaFixedRuntimeConfig.OllamaConfig ollamaFixedConfig = correspondingOllamaFixedConfig(fixedRuntimeConfig,
                configName);

        if (ollamaConfig.enableIntegration()) {
            EmbeddingModelConfig embeddingModelConfig = ollamaConfig.embeddingModel();
            Options.Builder optionsBuilder = Options.builder()
                    .temperature(embeddingModelConfig.temperature())
                    .topK(embeddingModelConfig.topK())
                    .topP(embeddingModelConfig.topP())
                    .numPredict(embeddingModelConfig.numPredict());

            if (embeddingModelConfig.stop().isPresent()) {
                optionsBuilder.stop(embeddingModelConfig.stop().get());
            }

            var builder = OllamaEmbeddingModel.builder()
                    .baseUrl(ollamaConfig.baseUrl().orElse(DEFAULT_BASE_URL))
                    .tlsConfigurationName(ollamaConfig.tlsConfigurationName().orElse(null))
                    .timeout(ollamaConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .model(ollamaFixedConfig.embeddingModel().modelId())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), ollamaConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), ollamaConfig.logResponses()))
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName);

            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
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

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig = correspondingOllamaConfig(runtimeConfig.getValue(), configName);
        LangChain4jOllamaFixedRuntimeConfig.OllamaConfig ollamaFixedConfig = correspondingOllamaFixedConfig(fixedRuntimeConfig,
                configName);

        if (ollamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = ollamaConfig.chatModel();

            Options.Builder optionsBuilder = Options.builder()
                    .temperature(chatModelConfig.temperature())
                    .topK(chatModelConfig.topK())
                    .topP(chatModelConfig.topP());

            if (chatModelConfig.numPredict().isPresent()) {
                optionsBuilder.numPredict(chatModelConfig.numPredict().getAsInt());
            }
            if (chatModelConfig.stop().isPresent()) {
                optionsBuilder.stop(chatModelConfig.stop().get());
            }
            if (chatModelConfig.seed().isPresent()) {
                optionsBuilder.seed(chatModelConfig.seed().get());
            }

            ChatModelConfig.ModelOptionsConfig modelOptions = chatModelConfig.modelOptions();
            if (modelOptions.think().isPresent()) {
                optionsBuilder.option("think", modelOptions.think().get());
            }
            if (modelOptions.returnThinking().isPresent()) {
                optionsBuilder.option("returnThinking", modelOptions.returnThinking().get());
            }
            if (modelOptions.numCtx().isPresent()) {
                optionsBuilder.option("numCtx", modelOptions.numCtx().getAsInt());
            }
            if (modelOptions.repeatLastN().isPresent()) {
                optionsBuilder.option("repeatLastN", modelOptions.repeatLastN().getAsInt());
            }
            if (modelOptions.repeatPenalty().isPresent()) {
                optionsBuilder.option("repeatPenalty", modelOptions.repeatPenalty().getAsDouble());
            }
            if (modelOptions.mirostat().isPresent()) {
                optionsBuilder.option("mirostat", modelOptions.mirostat().getAsInt());
            }
            if (modelOptions.mirostatEta().isPresent()) {
                optionsBuilder.option("mirostatEta", modelOptions.mirostatEta().getAsDouble());
            }
            if (modelOptions.mirostatTau().isPresent()) {
                optionsBuilder.option("mirostatTau", modelOptions.mirostatTau().getAsDouble());
            }
            if (modelOptions.minP().isPresent()) {
                optionsBuilder.option("minP", modelOptions.minP().getAsDouble());
            }

            var builder = OllamaStreamingChatLanguageModel.builder()
                    .baseUrl(ollamaConfig.baseUrl().orElse(DEFAULT_BASE_URL))
                    .tlsConfigurationName(ollamaConfig.tlsConfigurationName().orElse(null))
                    .timeout(ollamaConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), ollamaConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), ollamaConfig.logResponses()))
                    .logCurl(firstOrDefault(false, ollamaConfig.logRequestsCurl()))
                    .model(ollamaFixedConfig.chatModel().modelId())
                    .options(optionsBuilder.build())
                    .configName(NamedConfigUtil.isDefault(configName) ? null : configName);

            return new Function<>() {
                @Override
                public StreamingChatModel apply(
                        SyntheticCreationalContext<StreamingChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
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
                        SyntheticCreationalContext<StreamingChatModel> context) {
                    return new DisabledStreamingChatModel();
                }
            };
        }
    }

    private LangChain4jOllamaConfig.OllamaConfig correspondingOllamaConfig(LangChain4jOllamaConfig runtimeConfig,
            String configName) {
        LangChain4jOllamaConfig.OllamaConfig ollamaConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            ollamaConfig = runtimeConfig.defaultConfig();
        } else {
            ollamaConfig = runtimeConfig.namedConfig().get(configName);
        }
        return ollamaConfig;
    }

    private LangChain4jOllamaFixedRuntimeConfig.OllamaConfig correspondingOllamaFixedConfig(
            LangChain4jOllamaFixedRuntimeConfig runtimeConfig,
            String configName) {
        LangChain4jOllamaFixedRuntimeConfig.OllamaConfig ollamaConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            ollamaConfig = runtimeConfig.defaultConfig();
        } else {
            ollamaConfig = runtimeConfig.namedConfig().get(configName);
        }
        return ollamaConfig;
    }
}
