package io.quarkiverse.langchain4j.huggingface.runtime;

import java.net.URL;
import java.util.Optional;
import java.util.function.Supplier;

import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceChatModel;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceEmbeddingModel;
import io.quarkiverse.langchain4j.huggingface.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.Langchain4jHuggingFaceConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class HuggingFaceRecorder {
    public Supplier<?> chatModel(Langchain4jHuggingFaceConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        URL url = runtimeConfig.chatModel().inferenceEndpointUrl();

        if (apiKeyOpt.isEmpty() && url.toExternalForm().contains("api-inference.huggingface.co")) { // when using the default base URL an API key is required
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }

        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = QuarkusHuggingFaceChatModel.builder()
                .url(url)
                .timeout(runtimeConfig.timeout())
                .temperature(chatModelConfig.temperature())
                .waitForModel(chatModelConfig.waitForModel())
                .doSample(chatModelConfig.doSample())
                .topP(chatModelConfig.topP())
                .topK(chatModelConfig.topK())
                .repetitionPenalty(chatModelConfig.repetitionPenalty())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses());

        if (apiKeyOpt.isPresent()) {
            builder.accessToken(apiKeyOpt.get());
        }
        if (chatModelConfig.returnFullText().isPresent()) {
            builder.returnFullText(chatModelConfig.returnFullText().get());
        }

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
        EmbeddingModelConfig embeddingModelConfig = runtimeConfig.embeddingModel();
        Optional<URL> urlOpt = embeddingModelConfig.inferenceEndpointUrl();
        if (urlOpt.isEmpty()) {
            return null;
        }
        if (apiKeyOpt.isEmpty() && urlOpt.isPresent()
                && urlOpt.get().toExternalForm().contains("api-inference.huggingface.co")) { // when using the default base URL an API key is required
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        var builder = QuarkusHuggingFaceEmbeddingModel.builder()
                .url(urlOpt.get())
                .timeout(runtimeConfig.timeout())
                .waitForModel(embeddingModelConfig.waitForModel());

        if (apiKeyOpt.isPresent()) {
            builder.accessToken(apiKeyOpt.get());
        }

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
