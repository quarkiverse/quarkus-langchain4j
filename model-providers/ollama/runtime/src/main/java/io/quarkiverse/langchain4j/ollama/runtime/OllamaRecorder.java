package io.quarkiverse.langchain4j.ollama.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

            Map<String, String> additionalOptions = chatModelConfig.options();
            if (additionalOptions != null && !additionalOptions.isEmpty()) {
                for (Map.Entry<String, String> entry : additionalOptions.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    applyOptionToBuilder(ollamaChatModelBuilder, key, value);
                }
            }

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    ollamaChatModelBuilder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));

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

    public Supplier<EmbeddingModel> embeddingModel(String configName) {
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

            Map<String, String> additionalOptions = chatModelConfig.options();
            if (additionalOptions != null && !additionalOptions.isEmpty()) {
                for (Map.Entry<String, String> entry : additionalOptions.entrySet()) {
                    optionsBuilder.option(entry.getKey(), convertOptionValue(entry.getKey(), entry.getValue()));
                }
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

    private static void applyOptionToBuilder(OllamaChatModel.OllamaChatModelBuilder builder, String key, String value) {
        switch (key) {
            case "think":
                builder.think(Boolean.parseBoolean(value));
                break;
            case "returnThinking":
                builder.returnThinking(Boolean.parseBoolean(value));
                break;
            case "numCtx":
                builder.numCtx(Integer.parseInt(value));
                break;
            case "numPredict":
                builder.numPredict(Integer.parseInt(value));
                break;
            case "repeatLastN":
                builder.repeatLastN(Integer.parseInt(value));
                break;
            case "repeatPenalty":
                builder.repeatPenalty(Double.parseDouble(value));
                break;
            case "mirostat":
                builder.mirostat(Integer.parseInt(value));
                break;
            case "mirostatEta":
                builder.mirostatEta(Double.parseDouble(value));
                break;
            case "mirostatTau":
                builder.mirostatTau(Double.parseDouble(value));
                break;
            case "minP":
                builder.minP(Double.parseDouble(value));
                break;
            case "seed":
                builder.seed(Integer.parseInt(value));
                break;
            default:
                break;
        }
    }

    private static Object convertOptionValue(String key, String value) {
        return switch (key) {
            case "think", "returnThinking" -> Boolean.parseBoolean(value);
            case "numCtx", "numPredict", "repeatLastN", "mirostat", "seed" -> Integer.parseInt(value);
            case "repeatPenalty", "mirostatEta", "mirostatTau", "minP", "temperature", "topP" -> Double.parseDouble(value);
            default -> value;
        };
    }
}
