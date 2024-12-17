package io.quarkiverse.langchain4j.mistralai.runtime;

import static io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig.MistralAiConfig.DEFAULT_API_KEY;
import static io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig.MistralAiConfig.DEFAULT_BASE_URL;
import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.time.Duration;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.mistralai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class MistralAiRecorder {

    public Supplier<ChatLanguageModel> chatModel(LangChain4jMistralAiConfig runtimeConfig, String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(runtimeConfig,
                configName);

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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jMistralAiConfig runtimeConfig,
            String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(runtimeConfig,
                configName);

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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jMistralAiConfig runtimeConfig, String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(runtimeConfig,
                configName);

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

    public Supplier<ModerationModel> moderationModel(LangChain4jMistralAiConfig runtimeConfig, String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(runtimeConfig,
                configName);

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

    private LangChain4jMistralAiConfig.MistralAiConfig correspondingMistralAiConfig(
            LangChain4jMistralAiConfig runtimeConfig, String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig config;
        if (NamedConfigUtil.isDefault(configName)) {
            config = runtimeConfig.defaultConfig();
        } else {
            config = runtimeConfig.namedConfig().get(configName);
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
