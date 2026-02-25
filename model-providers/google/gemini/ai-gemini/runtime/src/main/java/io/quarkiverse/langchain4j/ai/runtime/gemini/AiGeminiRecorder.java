package io.quarkiverse.langchain4j.ai.runtime.gemini;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.Optional;
import java.util.function.Function;
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
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import io.quarkiverse.langchain4j.ai.runtime.gemini.config.ChatModelConfig;
import io.quarkiverse.langchain4j.ai.runtime.gemini.config.LangChain4jAiGeminiConfig;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class AiGeminiRecorder {
    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelAuthProvider>> MODEL_AUTH_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jAiGeminiConfig> runtimeConfig;

    public AiGeminiRecorder(RuntimeValue<LangChain4jAiGeminiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
        var aiConfig = correspondingAiConfig(configName);

        if (aiConfig.enableIntegration()) {
            var embeddingModelConfig = aiConfig.embeddingModel();
            Optional<String> baseUrl = aiConfig.baseUrl();

            String apiKey = aiConfig.apiKey().orElse(null);

            var httpClientBuilder = new JaxRsHttpClientBuilder();

            var builder = GoogleAiEmbeddingModel.builder()
                    .httpClientBuilder(httpClientBuilder)
                    .baseUrl(determineBaseUrl(baseUrl))
                    .apiKey(apiKey)
                    .modelName(embeddingModelConfig.modelId())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), aiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), aiConfig.logResponses()));

            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                    throwIfApiKeysNotConfigured(apiKey, isAuthProviderAvailable(context, configName),
                            configName);
                    if (apiKey == null) {
                        httpClientBuilder.addClientProvider(new ModelAuthProviderFilter(embeddingModelConfig.modelId()));
                    }

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

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        var aiConfig = correspondingAiConfig(configName);

        if (aiConfig.enableIntegration()) {
            var chatModelConfig = aiConfig.chatModel();
            Optional<String> baseUrl = aiConfig.baseUrl();

            var httpClientBuilder = new JaxRsHttpClientBuilder();

            String apiKey = aiConfig.apiKey().orElse(null);
            var builder = GoogleAiGeminiChatModel.builder()
                    .httpClientBuilder(httpClientBuilder)
                    .baseUrl(determineBaseUrl(baseUrl))
                    .apiKey(apiKey)
                    .modelName(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), aiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), aiConfig.logResponses()));

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topK().isPresent()) {
                builder.topK(chatModelConfig.topK().getAsInt());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.timeout().isPresent()) {
                builder.timeout(chatModelConfig.timeout().get());
            }

            configureThinking(chatModelConfig, builder);
            // TODO: what do we do with these?
            builder.returnThinking(true);
            builder.sendThinking(true);

            // TODO: add the rest of the properties

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    throwIfApiKeysNotConfigured(apiKey, isAuthProviderAvailable(context, configName),
                            configName);

                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));

                    if (apiKey == null) {
                        httpClientBuilder.addClientProvider(new ModelAuthProviderFilter(chatModelConfig.modelId()));
                    }

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

    private String determineBaseUrl(Optional<String> baseUrl) {
        return baseUrl.orElse("https://generativelanguage.googleapis.com");
    }

    private void configureThinking(ChatModelConfig chatModelConfig,
            GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder builder) {
        GeminiThinkingConfig.Builder geminiThinkingConfigBuilder = GeminiThinkingConfig.builder()
                .includeThoughts(chatModelConfig.thinking().includeThoughts());
        if (chatModelConfig.thinking().thinkingBudget().isPresent()) {
            geminiThinkingConfigBuilder.thinkingBudget(chatModelConfig.thinking().thinkingBudget().getAsInt());
        }
        builder.thinkingConfig(geminiThinkingConfigBuilder.build());
    }

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        var aiConfig = correspondingAiConfig(configName);

        if (aiConfig.enableIntegration()) {
            var chatModelConfig = aiConfig.chatModel();
            Optional<String> baseUrl = aiConfig.baseUrl();

            String apiKey = aiConfig.apiKey().orElse(null);

            var httpClientBuilder = new JaxRsHttpClientBuilder();

            var builder = GoogleAiGeminiStreamingChatModel.builder()
                    .baseUrl(determineBaseUrl(baseUrl))
                    .httpClientBuilder(httpClientBuilder)
                    .apiKey(apiKey)
                    .modelName(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), aiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), aiConfig.logResponses()));

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topK().isPresent()) {
                builder.topK(chatModelConfig.topK().getAsInt());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.timeout().isPresent()) {
                builder.timeout(chatModelConfig.timeout().get());
            }

            configureThinking(chatModelConfig, builder);
            // TODO: what do we do with these?
            builder.returnThinking(true);
            builder.sendThinking(true);

            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    throwIfApiKeysNotConfigured(apiKey, isAuthProviderAvailable(context, configName),
                            configName);

                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));

                    if (apiKey == null) {
                        httpClientBuilder.addClientProvider(new ModelAuthProviderFilter(chatModelConfig.modelId()));
                    }

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

    private void throwIfApiKeysNotConfigured(String apiKey, boolean authProviderAvailable, String configName) {
        if (apiKey == null && !authProviderAvailable) {
            throw new ConfigValidationException(createConfigProblems("api-key", configName));
        }
    }

    private static <T> boolean isAuthProviderAvailable(SyntheticCreationalContext<T> context, String configName) {
        return context.getInjectedReference(MODEL_AUTH_PROVIDER_TYPE_LITERAL).isResolvable();
    }

    private LangChain4jAiGeminiConfig.AiGeminiConfig correspondingAiConfig(String configName) {

        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.ai.gemini%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
