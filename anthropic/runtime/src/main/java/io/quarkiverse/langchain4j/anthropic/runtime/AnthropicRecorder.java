package io.quarkiverse.langchain4j.anthropic.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.function.Supplier;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import io.quarkiverse.langchain4j.anthropic.runtime.config.LangChain4jAnthropicConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class AnthropicRecorder {
    private static final String DUMMY_KEY = "dummy";

    public Supplier<ChatLanguageModel> chatModel(LangChain4jAnthropicConfig runtimeConfig, String modelName) {
        var anthropicConfig = correspondingAnthropicConfig(runtimeConfig, modelName);

        if (anthropicConfig.enableIntegration()) {
            var chatModelConfig = anthropicConfig.chatModel();
            var apiKey = anthropicConfig.apiKey();

            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
            }

            var builder = AnthropicChatModel.builder()
                    .baseUrl(anthropicConfig.baseUrl())
                    .apiKey(apiKey)
                    .version(anthropicConfig.version())
                    .modelName(chatModelConfig.modelName())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), anthropicConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), anthropicConfig.logResponses()))
                    .timeout(anthropicConfig.timeout())
                    .topK(chatModelConfig.topK())
                    .maxTokens(chatModelConfig.maxTokens())
                    .maxRetries(chatModelConfig.maxRetries());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }

            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }

            if (chatModelConfig.stopSequences().isPresent()) {
                builder.stopSequences(chatModelConfig.stopSequences().get());
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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jAnthropicConfig runtimeConfig, String modelName) {
        var anthropicConfig = correspondingAnthropicConfig(runtimeConfig, modelName);

        if (anthropicConfig.enableIntegration()) {
            var chatModelConfig = anthropicConfig.chatModel();
            var apiKey = anthropicConfig.apiKey();

            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
            }

            var builder = AnthropicStreamingChatModel.builder()
                    .baseUrl(anthropicConfig.baseUrl())
                    .apiKey(apiKey)
                    .version(anthropicConfig.version())
                    .modelName(chatModelConfig.modelName())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), anthropicConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), anthropicConfig.logResponses()))
                    .timeout(anthropicConfig.timeout())
                    .topK(chatModelConfig.topK())
                    .maxTokens(chatModelConfig.maxTokens());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }

            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }

            if (chatModelConfig.stopSequences().isPresent()) {
                builder.stopSequences(chatModelConfig.stopSequences().get());
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

    private LangChain4jAnthropicConfig.AnthropicConfig correspondingAnthropicConfig(
            LangChain4jAnthropicConfig runtimeConfig, String modelName) {

        return NamedModelUtil.isDefault(modelName) ? runtimeConfig.defaultConfig() : runtimeConfig.namedConfig().get(modelName);
    }

    private static ConfigValidationException.Problem[] createApiKeyConfigProblem(String modelName) {
        return createConfigProblems("api-key", modelName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String modelName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, modelName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.anthropic%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
    }
}
