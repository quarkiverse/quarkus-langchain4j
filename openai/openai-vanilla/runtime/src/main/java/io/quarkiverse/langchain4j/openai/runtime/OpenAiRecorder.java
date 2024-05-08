package io.quarkiverse.langchain4j.openai.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiImageModel;
import io.quarkiverse.langchain4j.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ImageModelConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenAiRecorder {

    private static final String DUMMY_KEY = "dummy";
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1/";

    public Supplier<ChatLanguageModel> chatModel(LangChain4jOpenAiConfig runtimeConfig, String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            ChatModelConfig chatModelConfig = openAiConfig.chatModel();
            var builder = OpenAiChatModel.builder()
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout())
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jOpenAiConfig runtimeConfig, String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            ChatModelConfig chatModelConfig = openAiConfig.chatModel();
            var builder = OpenAiStreamingChatModel.builder()
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jOpenAiConfig runtimeConfig, String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            EmbeddingModelConfig embeddingModelConfig = openAiConfig.embeddingModel();
            var builder = OpenAiEmbeddingModel.builder()
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout())
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(embeddingModelConfig.logRequests().orElse(false))
                    .logResponses(embeddingModelConfig.logResponses().orElse(false))
                    .modelName(embeddingModelConfig.modelName());

            if (embeddingModelConfig.user().isPresent()) {
                builder.user(embeddingModelConfig.user().get());
            }

            openAiConfig.organizationId().ifPresent(builder::organizationId);

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

    public Supplier<ModerationModel> moderationModel(LangChain4jOpenAiConfig runtimeConfig, String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            ModerationModelConfig moderationModelConfig = openAiConfig.moderationModel();
            var builder = OpenAiModerationModel.builder()
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout())
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(moderationModelConfig.logRequests().orElse(false))
                    .logResponses(moderationModelConfig.logResponses().orElse(false))
                    .modelName(moderationModelConfig.modelName());

            openAiConfig.organizationId().ifPresent(builder::organizationId);

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

    public Supplier<ImageModel> imageModel(LangChain4jOpenAiConfig runtimeConfig, String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = correspondingOpenAiConfig(runtimeConfig, configName);

        if (openAiConfig.enableIntegration()) {
            String apiKey = openAiConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey) && OPENAI_BASE_URL.equals(openAiConfig.baseUrl())) {
                throw new ConfigValidationException(createApiKeyConfigProblems(configName));
            }
            ImageModelConfig imageModelConfig = openAiConfig.imageModel();
            var builder = QuarkusOpenAiImageModel.builder()
                    .baseUrl(openAiConfig.baseUrl())
                    .apiKey(apiKey)
                    .timeout(openAiConfig.timeout())
                    .maxRetries(openAiConfig.maxRetries())
                    .logRequests(imageModelConfig.logRequests().orElse(false))
                    .logResponses(imageModelConfig.logResponses().orElse(false))
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
                    persistDirectory = imageModelConfig.persistDirectory().or(new Supplier<>() {
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
                public ImageModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public ImageModel get() {
                    return new DisabledImageModel();
                }
            };
        }
    }

    private LangChain4jOpenAiConfig.OpenAiConfig correspondingOpenAiConfig(LangChain4jOpenAiConfig runtimeConfig,
            String configName) {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            openAiConfig = runtimeConfig.defaultConfig();
        } else {
            openAiConfig = runtimeConfig.namedConfig().get(configName);
        }
        return openAiConfig;
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblems(String configName) {
        return createConfigProblems("api-key", configName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.openai%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
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
