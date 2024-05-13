package io.quarkiverse.langchain4j.mistralai.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import io.quarkiverse.langchain4j.mistralai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class MistralAiRecorder {
    private static final String DUMMY_KEY = "dummy";

    public Supplier<ChatLanguageModel> chatModel(LangChain4jMistralAiConfig runtimeConfig, String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig mistralAiConfig = correspondingMistralAiConfig(runtimeConfig,
                configName);

        if (mistralAiConfig.enableIntegration()) {
            String apiKey = mistralAiConfig.apiKey();
            ChatModelConfig chatModelConfig = mistralAiConfig.chatModel();

            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = MistralAiChatModel.builder()
                    .baseUrl(mistralAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .modelName(chatModelConfig.modelName())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
                    .timeout(mistralAiConfig.timeout());

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
            String apiKey = mistralAiConfig.apiKey();
            ChatModelConfig chatModelConfig = mistralAiConfig.chatModel();

            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = MistralAiStreamingChatModel.builder()
                    .baseUrl(mistralAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .modelName(chatModelConfig.modelName())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
                    .timeout(mistralAiConfig.timeout());

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
            String apiKey = mistralAiConfig.apiKey();
            EmbeddingModelConfig embeddingModelConfig = mistralAiConfig.embeddingModel();

            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = MistralAiEmbeddingModel.builder()
                    .baseUrl(mistralAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .modelName(embeddingModelConfig.modelName())
                    .logRequests(embeddingModelConfig.logRequests().orElse(false))
                    .logResponses(embeddingModelConfig.logResponses().orElse(false))
                    .timeout(mistralAiConfig.timeout());

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

    private LangChain4jMistralAiConfig.MistralAiConfig correspondingMistralAiConfig(
            LangChain4jMistralAiConfig runtimeConfig, String configName) {
        LangChain4jMistralAiConfig.MistralAiConfig huggingFaceConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            huggingFaceConfig = runtimeConfig.defaultConfig();
        } else {
            huggingFaceConfig = runtimeConfig.namedConfig().get(configName);
        }
        return huggingFaceConfig;
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
