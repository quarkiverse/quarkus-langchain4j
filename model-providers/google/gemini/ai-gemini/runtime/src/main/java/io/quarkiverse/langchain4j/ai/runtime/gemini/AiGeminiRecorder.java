package io.quarkiverse.langchain4j.ai.runtime.gemini;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ai.runtime.gemini.config.LangChain4jAiGeminiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class AiGeminiRecorder {
    private static final String DUMMY_KEY = "dummy";

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jAiGeminiConfig config, String configName) {
        var aiConfig = correspondingAiConfig(config, configName);

        if (aiConfig.enableIntegration()) {
            var embeddingModelConfig = aiConfig.embeddingModel();
            Optional<String> baseUrl = aiConfig.baseUrl();

            String key = aiConfig.apiKey();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(key)) {
                throw new ConfigValidationException(createConfigProblems("api-key", configName));
            }

            var builder = AiGeminiEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .key(key)
                    .modelId(embeddingModelConfig.modelId())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), aiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), aiConfig.logResponses()));

            if (embeddingModelConfig.outputDimension().isPresent()) {
                builder.dimension(embeddingModelConfig.outputDimension().get());
            }

            if (embeddingModelConfig.taskType().isPresent()) {
                builder.taskType(embeddingModelConfig.taskType().get());
            }

            return builder::build;
        } else {
            return DisabledEmbeddingModel::new;
        }
    }

    public Supplier<ChatLanguageModel> chatModel(LangChain4jAiGeminiConfig config, String configName) {
        var aiConfig = correspondingAiConfig(config, configName);

        if (aiConfig.enableIntegration()) {
            var chatModelConfig = aiConfig.chatModel();
            Optional<String> baseUrl = aiConfig.baseUrl();

            String key = aiConfig.apiKey();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(key)) {
                throw new ConfigValidationException(createConfigProblems("api-key", configName));
            }
            var builder = AiGeminiChatLanguageModel.builder()
                    .baseUrl(baseUrl)
                    .key(key)
                    .modelId(chatModelConfig.modelId())
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

            // TODO: add the rest of the properties

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

    private LangChain4jAiGeminiConfig.AiGeminiConfig correspondingAiConfig(
            LangChain4jAiGeminiConfig runtimeConfig, String configName) {

        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.defaultConfig()
                : runtimeConfig.namedConfig().get(configName);
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
