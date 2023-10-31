package io.quarkiverse.langchain4j.huggingface.runtime;

import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import io.quarkiverse.langchain4j.huggingface.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.Langchain4jHuggingFaceConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class HuggingFaceRecorder {
    public Supplier<?> chatModel(Langchain4jHuggingFaceConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        if (apiKeyOpt.isEmpty()) {
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = HuggingFaceChatModel.builder()
                .accessToken(apiKeyOpt.get())
                .timeout(runtimeConfig.timeout())
                .modelId(chatModelConfig.modelId())
                .temperature(chatModelConfig.temperature())
                .returnFullText(chatModelConfig.returnFullText())
                .waitForModel(chatModelConfig.waitForModel());

        if (chatModelConfig.maxNewTokens().isPresent()) {
            builder.maxNewTokens(chatModelConfig.maxNewTokens().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> embeddingModel(Langchain4jHuggingFaceConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        if (apiKeyOpt.isEmpty()) {
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        EmbeddingModelConfig embeddingModelConfig = runtimeConfig.embeddingModel();
        var builder = HuggingFaceChatModel.builder()
                .accessToken(apiKeyOpt.get())
                .timeout(runtimeConfig.timeout())
                .modelId(embeddingModelConfig.modelId())
                .waitForModel(embeddingModelConfig.waitForModel());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblems() {
        return createConfigProblems("api-key");
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key) };
    }

    private ConfigValidationException.Problem createConfigProblem(String key) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.huggingface.%s is required but it could not be found in any config source",
                key));
    }
}
