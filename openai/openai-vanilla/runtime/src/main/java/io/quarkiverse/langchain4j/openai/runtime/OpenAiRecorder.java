package io.quarkiverse.langchain4j.openai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiImageModel;
import io.quarkiverse.langchain4j.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ImageModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.Langchain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ModerationModelConfig;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenAiRecorder {

    public Supplier<?> chatModel(Langchain4jOpenAiConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        if (apiKeyOpt.isEmpty()) {
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = OpenAiChatModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .apiKey(apiKeyOpt.get())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), runtimeConfig.logResponses()))
                .modelName(chatModelConfig.modelName())
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .presencePenalty(chatModelConfig.presencePenalty())
                .frequencyPenalty(chatModelConfig.frequencyPenalty());

        runtimeConfig.organizationId().ifPresent(builder::organizationId);

        if (chatModelConfig.maxTokens().isPresent()) {
            builder.maxTokens(chatModelConfig.maxTokens().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> streamingChatModel(Langchain4jOpenAiConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        if (apiKeyOpt.isEmpty()) {
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .apiKey(apiKeyOpt.get())
                .timeout(runtimeConfig.timeout())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), runtimeConfig.logResponses()))
                .modelName(chatModelConfig.modelName())
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .presencePenalty(chatModelConfig.presencePenalty())
                .frequencyPenalty(chatModelConfig.frequencyPenalty());

        runtimeConfig.organizationId().ifPresent(builder::organizationId);

        if (chatModelConfig.maxTokens().isPresent()) {
            builder.maxTokens(chatModelConfig.maxTokens().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> embeddingModel(Langchain4jOpenAiConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        if (apiKeyOpt.isEmpty()) {
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        EmbeddingModelConfig embeddingModelConfig = runtimeConfig.embeddingModel();
        var builder = OpenAiEmbeddingModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .apiKey(apiKeyOpt.get())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), runtimeConfig.logResponses()))
                .modelName(embeddingModelConfig.modelName());

        runtimeConfig.organizationId().ifPresent(builder::organizationId);

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> moderationModel(Langchain4jOpenAiConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        if (apiKeyOpt.isEmpty()) {
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        ModerationModelConfig moderationModelConfig = runtimeConfig.moderationModel();
        var builder = OpenAiModerationModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .apiKey(apiKeyOpt.get())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(firstOrDefault(false, moderationModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, moderationModelConfig.logResponses(), runtimeConfig.logResponses()))
                .modelName(moderationModelConfig.modelName());

        runtimeConfig.organizationId().ifPresent(builder::organizationId);

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> imageModel(Langchain4jOpenAiConfig runtimeConfig) {
        Optional<String> apiKeyOpt = runtimeConfig.apiKey();
        if (apiKeyOpt.isEmpty()) {
            throw new ConfigValidationException(createApiKeyConfigProblems());
        }
        ImageModelConfig imageModelConfig = runtimeConfig.imageModel();
        var builder = QuarkusOpenAiImageModel.builder()
                .baseUrl(runtimeConfig.baseUrl())
                .apiKey(apiKeyOpt.get())
                .timeout(runtimeConfig.timeout())
                .maxRetries(runtimeConfig.maxRetries())
                .logRequests(firstOrDefault(false, imageModelConfig.logRequests(), runtimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, imageModelConfig.logResponses(), runtimeConfig.logResponses()))
                .modelName(imageModelConfig.modelName())
                .size(imageModelConfig.size())
                .quality(imageModelConfig.quality())
                .style(imageModelConfig.style())
                .responseFormat(imageModelConfig.responseFormat())
                .user(imageModelConfig.user());

        runtimeConfig.organizationId().ifPresent(builder::organizationId);

        // we persist if the directory was set explicitly and the boolean flag was not set to false
        // or if the boolean flag was set explicitly to true
        Optional<Path> persistDirectory = Optional.empty();
        if (imageModelConfig.persist().isPresent()) {
            if (imageModelConfig.persist().get()) {
                persistDirectory = imageModelConfig.persistDirectory()
                        .or(new Supplier<>() {
                            @Override
                            public Optional<? extends Path> get() {
                                return Optional.of(Paths.get(System.getProperty("java.io.tmpdir"), "dall-e-images"));
                            }
                        });
            }
        } else {
            if (imageModelConfig.persistDirectory().isPresent()) {
                persistDirectory = imageModelConfig.persistDirectory();
            }
        }

        builder.persistDirectory(persistDirectory);

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
                "SRCFG00014: The config property quarkus.langchain4j.openai.%s is required but it could not be found in any config source",
                key));
    }

    public void cleanUp(ShutdownContext shutdown) {
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                QuarkusOpenAiClient.clearCache();
            }
        });
    }
}
