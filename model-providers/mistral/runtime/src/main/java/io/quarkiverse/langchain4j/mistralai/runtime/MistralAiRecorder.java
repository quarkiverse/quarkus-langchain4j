package io.quarkiverse.langchain4j.mistralai.runtime;

import static io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig.MistralAiConfig.DEFAULT_API_KEY;
import static io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig.MistralAiConfig.DEFAULT_BASE_URL;
import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.time.Duration;
import java.util.function.Function;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.mistralai.QuarkusMistralAiClient;
import io.quarkiverse.langchain4j.mistralai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class MistralAiRecorder {
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<MistralAiChatModel.MistralAiChatModelBuilder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<MistralAiEmbeddingModel.MistralAiEmbeddingModelBuilder>>> EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<MistralAiModerationModel.Builder>>> MODERATION_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jMistralAiConfig> runtimeConfig;

    public MistralAiRecorder(RuntimeValue<LangChain4jMistralAiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(configName);

        if (mistralAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = mistralAiConfig.chatModel();

            String apiKey = mistralAiConfig.apiKey();
            String baseUrl = mistralAiConfig.baseUrl();
            if (DEFAULT_API_KEY.equals(apiKey) && DEFAULT_BASE_URL.equals(baseUrl)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = MistralAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(chatModelConfig.modelName())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), mistralAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), mistralAiConfig.logResponses()))
                    .timeout(mistralAiConfig.timeout().orElse(Duration.ofSeconds(10)));

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().getAsInt());
            }
            if (chatModelConfig.safePrompt().isPresent()) {
                builder.safePrompt(chatModelConfig.safePrompt().get());
            }
            if (chatModelConfig.randomSeed().isPresent()) {
                builder.randomSeed(chatModelConfig.randomSeed().getAsInt());
            }

            var logCurl = firstOrDefault(false, mistralAiConfig.logRequestsCurl());

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    QuarkusMistralAiClient.setLogCurlHint(logCurl);
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

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(configName);

        if (mistralAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = mistralAiConfig.chatModel();

            String apiKey = mistralAiConfig.apiKey();
            String baseUrl = mistralAiConfig.baseUrl();
            if (DEFAULT_API_KEY.equals(apiKey) && DEFAULT_BASE_URL.equals(baseUrl)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = MistralAiStreamingChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(chatModelConfig.modelName())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), mistralAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), mistralAiConfig.logResponses()))
                    .timeout(mistralAiConfig.timeout().orElse(Duration.ofSeconds(10)));

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().getAsInt());
            }
            if (chatModelConfig.safePrompt().isPresent()) {
                builder.safePrompt(chatModelConfig.safePrompt().get());
            }
            if (chatModelConfig.randomSeed().isPresent()) {
                builder.randomSeed(chatModelConfig.randomSeed().getAsInt());
            }

            var logCurl = firstOrDefault(false, mistralAiConfig.logRequestsCurl());

            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    QuarkusMistralAiClient.setLogCurlHint(logCurl);
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

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(configName);

        if (mistralAiConfig.enableIntegration()) {
            EmbeddingModelConfig embeddingModelConfig = mistralAiConfig.embeddingModel();

            String apiKey = mistralAiConfig.apiKey();
            String baseUrl = mistralAiConfig.baseUrl();
            if (DEFAULT_API_KEY.equals(apiKey) && DEFAULT_BASE_URL.equals(baseUrl)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = MistralAiEmbeddingModel.builder()
                    .baseUrl(mistralAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .modelName(embeddingModelConfig.modelName())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), mistralAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), mistralAiConfig.logResponses()))
                    .timeout(mistralAiConfig.timeout().orElse(Duration.ofSeconds(10)));

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

    public Function<SyntheticCreationalContext<ModerationModel>, ModerationModel> moderationModel(String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(configName);

        if (mistralAiConfig.enableIntegration()) {
            ModerationModelConfig moderationModelConfig = mistralAiConfig.moderationModel();

            String apiKey = mistralAiConfig.apiKey();
            String baseUrl = mistralAiConfig.baseUrl();
            if (DEFAULT_API_KEY.equals(apiKey) && DEFAULT_BASE_URL.equals(baseUrl)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = new MistralAiModerationModel.Builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(moderationModelConfig.modelName())
                    .logRequests(firstOrDefault(false, moderationModelConfig.logRequests(), mistralAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, moderationModelConfig.logResponses(), mistralAiConfig.logResponses()))
                    .timeout(mistralAiConfig.timeout().orElse(Duration.ofSeconds(10)));

            var logCurl = firstOrDefault(false, mistralAiConfig.logRequestsCurl());

            return new Function<>() {
                @Override
                public ModerationModel apply(SyntheticCreationalContext<ModerationModel> context) {
                    QuarkusMistralAiClient.setLogCurlHint(logCurl);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(MODERATION_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
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

    private LangChain4jMistralAiConfig.MistralAiConfig correspondingMistralAiConfig(String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig config;
        if (NamedConfigUtil.isDefault(configName)) {
            config = runtimeConfig.getValue().defaultConfig();
        } else {
            config = runtimeConfig.getValue().namedConfig().get(configName);
        }
        return config;
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblem(String configName) {
        return createConfigProblems("api-key", configName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.mistralai%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
