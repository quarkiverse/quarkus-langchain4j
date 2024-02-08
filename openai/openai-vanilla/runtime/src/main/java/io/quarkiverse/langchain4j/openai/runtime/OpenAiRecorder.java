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
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenAiRecorder {

    private static final String DUMMY_KEY = "dummy";

    public Supplier<?> chatModel(Langchain4jOpenAiConfig runtimeConfig, String modelName) {
        Langchain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, modelName);
        String apiKey = openAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblems(modelName));
        }
        ChatModelConfig chatModelConfig = openAiConfig.chatModel();
        var builder = OpenAiChatModel.builder()
                .baseUrl(openAiConfig.baseUrl())
                .apiKey(apiKey)
                .timeout(openAiConfig.timeout())
                .maxRetries(openAiConfig.maxRetries())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), openAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), openAiConfig.logResponses()))
                .modelName(chatModelConfig.modelName())
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .presencePenalty(chatModelConfig.presencePenalty())
                .frequencyPenalty(chatModelConfig.frequencyPenalty())
                .responseFormat(chatModelConfig.responseFormat().orElse(null));

        openAiConfig.organizationId().ifPresent(builder::organizationId);

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

    public Supplier<?> streamingChatModel(Langchain4jOpenAiConfig runtimeConfig, String modelName) {
        Langchain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, modelName);
        String apiKey = openAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblems(modelName));
        }
        ChatModelConfig chatModelConfig = openAiConfig.chatModel();
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(openAiConfig.baseUrl())
                .apiKey(apiKey)
                .timeout(openAiConfig.timeout())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), openAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), openAiConfig.logResponses()))
                .modelName(chatModelConfig.modelName())
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .presencePenalty(chatModelConfig.presencePenalty())
                .frequencyPenalty(chatModelConfig.frequencyPenalty())
                .responseFormat(chatModelConfig.responseFormat().orElse(null));

        openAiConfig.organizationId().ifPresent(builder::organizationId);

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

    public Supplier<?> embeddingModel(Langchain4jOpenAiConfig runtimeConfig, String modelName) {
        Langchain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, modelName);
        String apiKeyOpt = openAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKeyOpt)) {
            throw new ConfigValidationException(createApiKeyConfigProblems(modelName));
        }
        EmbeddingModelConfig embeddingModelConfig = openAiConfig.embeddingModel();
        var builder = OpenAiEmbeddingModel.builder()
                .baseUrl(openAiConfig.baseUrl())
                .apiKey(apiKeyOpt)
                .timeout(openAiConfig.timeout())
                .maxRetries(openAiConfig.maxRetries())
                .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), openAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), openAiConfig.logResponses()))
                .modelName(embeddingModelConfig.modelName());

        if (embeddingModelConfig.user().isPresent()) {
            builder.user(embeddingModelConfig.user().get());
        }

        openAiConfig.organizationId().ifPresent(builder::organizationId);

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> moderationModel(Langchain4jOpenAiConfig runtimeConfig, String modelName) {
        Langchain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, modelName);
        String apiKey = openAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblems(modelName));
        }
        ModerationModelConfig moderationModelConfig = openAiConfig.moderationModel();
        var builder = OpenAiModerationModel.builder()
                .baseUrl(openAiConfig.baseUrl())
                .apiKey(apiKey)
                .timeout(openAiConfig.timeout())
                .maxRetries(openAiConfig.maxRetries())
                .logRequests(firstOrDefault(false, moderationModelConfig.logRequests(), openAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, moderationModelConfig.logResponses(), openAiConfig.logResponses()))
                .modelName(moderationModelConfig.modelName());

        openAiConfig.organizationId().ifPresent(builder::organizationId);

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> imageModel(Langchain4jOpenAiConfig runtimeConfig, String modelName) {
        Langchain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, modelName);
        String apiKey = openAiConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblems(modelName));
        }
        ImageModelConfig imageModelConfig = openAiConfig.imageModel();
        var builder = QuarkusOpenAiImageModel.builder()
                .baseUrl(openAiConfig.baseUrl())
                .apiKey(apiKey)
                .timeout(openAiConfig.timeout())
                .maxRetries(openAiConfig.maxRetries())
                .logRequests(firstOrDefault(false, imageModelConfig.logRequests(), openAiConfig.logRequests()))
                .logResponses(firstOrDefault(false, imageModelConfig.logResponses(), openAiConfig.logResponses()))
                .modelName(imageModelConfig.modelName())
                .size(imageModelConfig.size())
                .quality(imageModelConfig.quality())
                .style(imageModelConfig.style())
                .responseFormat(imageModelConfig.responseFormat())
                .user(imageModelConfig.user());

        openAiConfig.organizationId().ifPresent(builder::organizationId);

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

    private Langchain4jOpenAiConfig.OpenAiConfig correspondingOpenAiConfig(Langchain4jOpenAiConfig runtimeConfig,
            String modelName) {
        Langchain4jOpenAiConfig.OpenAiConfig openAiConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            openAiConfig = runtimeConfig.defaultConfig();
        } else {
            openAiConfig = runtimeConfig.namedConfig().get(modelName);
        }
        return openAiConfig;
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblems(String modelName) {
        return createConfigProblems("api-key", modelName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String modelName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, modelName) };
    }

    private ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.openai%s%s is required but it could not be found in any config source",
                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
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
