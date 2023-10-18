package io.quarkiverse.langchain4j.runtime;

import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiLanguageModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingLanguageModel;
import io.quarkiverse.langchain4j.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.config.ModelProvider;
import io.quarkiverse.langchain4j.runtime.config.OpenAiChatParams;
import io.quarkiverse.langchain4j.runtime.config.OpenAiEmbeddingParams;
import io.quarkiverse.langchain4j.runtime.config.OpenAiModerationParams;
import io.quarkiverse.langchain4j.runtime.config.OpenAiServer;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class BasicRecorder {

    public Supplier<?> chatModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
        if (modelProvider == ModelProvider.OPEN_AI) {
            OpenAiServer openAiServer = runtimeConfig.openAi();
            Optional<String> apiKeyOpt = openAiServer.apiKey();
            if (apiKeyOpt.isEmpty()) {
                throw new ConfigValidationException(createProblems("openai", "api-key"));
            }
            OpenAiChatParams openAiChatParams = runtimeConfig.chatModel().openAi();
            var builder = OpenAiChatModel.builder()
                    .baseUrl(openAiServer.baseUrl())
                    .apiKey(apiKeyOpt.get())
                    .timeout(openAiServer.timeout())
                    .maxRetries(openAiServer.maxRetries())
                    .logRequests(openAiServer.logRequests())
                    .logResponses(openAiServer.logResponses())

                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature())
                    .topP(openAiChatParams.topP())
                    .maxTokens(openAiChatParams.maxTokens())
                    .presencePenalty(openAiChatParams.presencePenalty())
                    .frequencyPenalty(openAiChatParams.frequencyPenalty());

            return new Supplier<>() {
                @Override
                public Object get() {
                    return builder.build();
                }
            };
        }
        //                else if (modelProvider == ModelProvider.LOCAL_AI) {
        //                    LocalAi localAi = runtimeConfig.localAi();
        //                    if (localAi.baseUrl().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("local", "base-url"));
        //                    }
        //                    LocalAiChatModel result = LocalAiChatModel.builder()
        //                            .baseUrl(localAi.baseUrl().get())
        //                            .modelName(localAi.modelName())
        //                            .temperature(localAi.temperature())
        //                            .topP(localAi.topP())
        //                            .maxTokens(localAi.maxTokens())
        //                            .timeout(localAi.timeout())
        //                            .maxRetries(localAi.maxRetries())
        //                            .logRequests(localAi.logRequests())
        //                            .logResponses(localAi.logResponses())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                }
        //                else if (modelProvider == ModelProvider.HUGGING_FACE) {
        //                    HuggingFace huggingFace = runtimeConfig.huggingFace();
        //                    if (huggingFace.accessToken().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("hugging-face", "access-token"));
        //                    }
        //                    HuggingFaceChatModel result = HuggingFaceChatModel.builder()
        //                            .accessToken(huggingFace.accessToken().get())
        //                            .modelId(huggingFace.modelId())
        //                            .timeout(huggingFace.timeout())
        //                            .temperature(huggingFace.temperature())
        //                            .maxNewTokens(huggingFace.maxNewTokens())
        //                            .returnFullText(huggingFace.returnFullText())
        //                            .waitForModel(huggingFace.waitForModel())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                }

        throw new IllegalStateException("Unsupported model provider " + modelProvider);
    }

    public Supplier<?> streamingChatModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
        if (modelProvider == ModelProvider.OPEN_AI) {
            OpenAiServer openAiServer = runtimeConfig.openAi();
            Optional<String> apiKeyOpt = openAiServer.apiKey();
            if (apiKeyOpt.isEmpty()) {
                throw new ConfigValidationException(createProblems("openai", "api-key"));
            }
            OpenAiChatParams openAiChatParams = runtimeConfig.languageModel().openAi();
            var builder = OpenAiStreamingChatModel.builder()
                    .baseUrl(openAiServer.baseUrl())
                    .apiKey(apiKeyOpt.get())
                    .timeout(openAiServer.timeout())
                    .logRequests(openAiServer.logRequests())
                    .logResponses(openAiServer.logResponses())

                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature())
                    .topP(openAiChatParams.topP())
                    .maxTokens(openAiChatParams.maxTokens())
                    .presencePenalty(openAiChatParams.presencePenalty())
                    .frequencyPenalty(openAiChatParams.frequencyPenalty());

            return new Supplier<>() {
                @Override
                public Object get() {
                    return builder.build();
                }
            };
        }
        //                else if (modelProvider == ModelProvider.LOCAL_AI) {
        //                    LocalAi localAi = runtimeConfig.localAi();
        //                    if (localAi.baseUrl().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("local", "base-url"));
        //                    }
        //                    LocalAiChatModel result = LocalAiChatModel.builder()
        //                            .baseUrl(localAi.baseUrl().get())
        //                            .modelName(localAi.modelName())
        //                            .temperature(localAi.temperature())
        //                            .topP(localAi.topP())
        //                            .maxTokens(localAi.maxTokens())
        //                            .timeout(localAi.timeout())
        //                            .maxRetries(localAi.maxRetries())
        //                            .logRequests(localAi.logRequests())
        //                            .logResponses(localAi.logResponses())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                } else if (modelProvider == ModelProvider.HUGGING_FACE) {
        //                    HuggingFace huggingFace = runtimeConfig.huggingFace();
        //                    if (huggingFace.accessToken().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("hugging-face", "access-token"));
        //                    }
        //                    HuggingFaceChatModel result = HuggingFaceChatModel.builder()
        //                            .accessToken(huggingFace.accessToken().get())
        //                            .modelId(huggingFace.modelId())
        //                            .timeout(huggingFace.timeout())
        //                            .temperature(huggingFace.temperature())
        //                            .maxNewTokens(huggingFace.maxNewTokens())
        //                            .returnFullText(huggingFace.returnFullText())
        //                            .waitForModel(huggingFace.waitForModel())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                }

        throw new IllegalStateException("Unsupported model provider " + modelProvider);
    }

    public Supplier<?> languageModel(ModelProvider modelProvider,
            LangChain4jRuntimeConfig runtimeConfig) {
        if (modelProvider == ModelProvider.OPEN_AI) {
            OpenAiServer openAiServer = runtimeConfig.openAi();
            Optional<String> apiKeyOpt = openAiServer.apiKey();
            if (apiKeyOpt.isEmpty()) {
                throw new ConfigValidationException(createProblems("openai", "api-key"));
            }
            OpenAiChatParams openAiChatParams = runtimeConfig.chatModel().openAi();
            var builder = OpenAiLanguageModel.builder()
                    .baseUrl(openAiServer.baseUrl())
                    .apiKey(apiKeyOpt.get())
                    .timeout(openAiServer.timeout())
                    .maxRetries(openAiServer.maxRetries())
                    .logRequests(openAiServer.logRequests())
                    .logResponses(openAiServer.logResponses())

                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature());

            return new Supplier<>() {
                @Override
                public Object get() {
                    return builder.build();
                }
            };
        }
        //                else if (modelProvider == ModelProvider.LOCAL_AI) {
        //                    LocalAi localAi = runtimeConfig.localAi();
        //                    if (localAi.baseUrl().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("local", "base-url"));
        //                    }
        //                    LocalAiLanguageModel result = LocalAiLanguageModel.builder()
        //                            .baseUrl(localAi.baseUrl().get())
        //                            .modelName(localAi.modelName())
        //                            .temperature(localAi.temperature())
        //                            .topP(localAi.topP())
        //                            .maxTokens(localAi.maxTokens())
        //                            .timeout(localAi.timeout())
        //                            .maxRetries(localAi.maxRetries())
        //                            .logRequests(localAi.logRequests())
        //                            .logResponses(localAi.logResponses())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                } else if (modelProvider == ModelProvider.HUGGING_FACE) {
        //                    HuggingFace huggingFace = runtimeConfig.huggingFace();
        //                    if (huggingFace.accessToken().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("hugging-face", "access-token"));
        //                    }
        //                    HuggingFaceLanguageModel result = HuggingFaceLanguageModel.builder()
        //                            .accessToken(huggingFace.accessToken().get())
        //                            .modelId(huggingFace.modelId())
        //                            .timeout(huggingFace.timeout())
        //                            .temperature(huggingFace.temperature())
        //                            .maxNewTokens(huggingFace.maxNewTokens())
        //                            .returnFullText(huggingFace.returnFullText())
        //                            .waitForModel(huggingFace.waitForModel())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                }

        throw new IllegalStateException("Unsupported model provider " + modelProvider);
    }

    public Supplier<?> streamingLanguageModel(ModelProvider modelProvider,
            LangChain4jRuntimeConfig runtimeConfig) {
        if (modelProvider == ModelProvider.OPEN_AI) {
            OpenAiServer openAi = runtimeConfig.openAi();
            Optional<String> apiKeyOpt = openAi.apiKey();
            if (apiKeyOpt.isEmpty()) {
                throw new ConfigValidationException(createProblems("openai", "api-key"));
            }
            OpenAiChatParams openAiChatParams = runtimeConfig.chatModel().openAi();
            var builder = OpenAiStreamingLanguageModel.builder()
                    .baseUrl(openAi.baseUrl())
                    .apiKey(apiKeyOpt.get())
                    .timeout(openAi.timeout())
                    .logRequests(openAi.logRequests())
                    .logResponses(openAi.logResponses())

                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature());

            return new Supplier<>() {
                @Override
                public Object get() {
                    return builder.build();
                }
            };
        }
        //                else if (modelProvider == ModelProvider.LOCAL_AI) {
        //                    LocalAi localAi = runtimeConfig.localAi();
        //                    if (localAi.baseUrl().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("local", "base-url"));
        //                    }
        //                    LocalAiLanguageModel result = LocalAiLanguageModel.builder()
        //                            .baseUrl(localAi.baseUrl().get())
        //                            .modelName(localAi.modelName())
        //                            .temperature(localAi.temperature())
        //                            .topP(localAi.topP())
        //                            .maxTokens(localAi.maxTokens())
        //                            .timeout(localAi.timeout())
        //                            .maxRetries(localAi.maxRetries())
        //                            .logRequests(localAi.logRequests())
        //                            .logResponses(localAi.logResponses())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                } else if (modelProvider == ModelProvider.HUGGING_FACE) {
        //                    HuggingFace huggingFace = runtimeConfig.huggingFace();
        //                    if (huggingFace.accessToken().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("hugging-face", "access-token"));
        //                    }
        //                    HuggingFaceLanguageModel result = HuggingFaceLanguageModel.builder()
        //                            .accessToken(huggingFace.accessToken().get())
        //                            .modelId(huggingFace.modelId())
        //                            .timeout(huggingFace.timeout())
        //                            .temperature(huggingFace.temperature())
        //                            .maxNewTokens(huggingFace.maxNewTokens())
        //                            .returnFullText(huggingFace.returnFullText())
        //                            .waitForModel(huggingFace.waitForModel())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                }

        throw new IllegalStateException("Unsupported model provider " + modelProvider);
    }

    public Supplier<?> embeddingModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
        if (modelProvider == ModelProvider.OPEN_AI) {
            OpenAiServer openAi = runtimeConfig.openAi();
            Optional<String> apiKeyOpt = openAi.apiKey();
            if (apiKeyOpt.isEmpty()) {
                throw new ConfigValidationException(createProblems("openai", "api-key"));
            }
            OpenAiEmbeddingParams modelParams = runtimeConfig.embeddingModel().openAi();
            var builder = OpenAiEmbeddingModel.builder()
                    .baseUrl(openAi.baseUrl())
                    .apiKey(apiKeyOpt.get())
                    .timeout(openAi.timeout())
                    .maxRetries(openAi.maxRetries())
                    .logRequests(openAi.logRequests())
                    .logResponses(openAi.logResponses())

                    .modelName(modelParams.modelName());

            return new Supplier<>() {
                @Override
                public Object get() {
                    return builder.build();
                }
            };
        }
        //                else if (modelProvider == ModelProvider.LOCAL_AI) {
        //                    LocalAi localAi = runtimeConfig.localAi();
        //                    if (localAi.baseUrl().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("local", "base-url"));
        //                    }
        //                    LocalAiEmbeddingModel result = LocalAiEmbeddingModel.builder()
        //                            .baseUrl(localAi.baseUrl().get())
        //                            .modelName(localAi.modelName())
        //                            .timeout(localAi.timeout())
        //                            .maxRetries(localAi.maxRetries())
        //                            .logRequests(localAi.logRequests())
        //                            .logResponses(localAi.logResponses())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                } else if (modelProvider == ModelProvider.HUGGING_FACE) {
        //                    HuggingFace huggingFace = runtimeConfig.huggingFace();
        //                    if (huggingFace.accessToken().isEmpty()) {
        //                        throw new ConfigValidationException(createProblems("hugging-face", "access-token"));
        //                    }
        //                    HuggingFaceEmbeddingModel result = HuggingFaceEmbeddingModel.builder()
        //                            .accessToken(huggingFace.accessToken().get())
        //                            .modelId(huggingFace.modelId())
        //                            .timeout(huggingFace.timeout())
        //                            .waitForModel(huggingFace.waitForModel())
        //                            .build();
        //                    return new RuntimeValue<>(result);
        //                }

        throw new IllegalStateException("Unsupported model provider " + modelProvider);
    }

    public Supplier<?> moderationModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
        if (modelProvider == ModelProvider.OPEN_AI) {
            OpenAiServer openAi = runtimeConfig.openAi();
            Optional<String> apiKeyOpt = openAi.apiKey();
            if (apiKeyOpt.isEmpty()) {
                throw new ConfigValidationException(createProblems("openai", "api-key"));
            }
            OpenAiModerationParams params = runtimeConfig.moderationModel().openAi();
            var builder = OpenAiModerationModel.builder()
                    .baseUrl(openAi.baseUrl())
                    .apiKey(apiKeyOpt.get())
                    .timeout(openAi.timeout())
                    .maxRetries(openAi.maxRetries())
                    .logRequests(openAi.logRequests())
                    .logResponses(openAi.logResponses())

                    .modelName(params.modelName());

            return new Supplier<>() {
                @Override
                public Object get() {
                    return builder.build();
                }
            };
        }

        throw new IllegalStateException("Unsupported model provider " + modelProvider);
    }

    private ConfigValidationException.Problem[] createProblems(String namespace, String key) {
        return new ConfigValidationException.Problem[] { createProblem(namespace, key) };
    }

    private ConfigValidationException.Problem createProblem(String namespace, String key) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.%s.%s is required but it could not be found in any config source",
                namespace, key));
    }

    public void cleanUp(ShutdownContext shutdown) {
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                QuarkusOpenAiClient.clearCache();
                StructuredPromptsRecorder.clearTemplates();
                AiServicesRecorder.clearMetadata();
                ToolsRecorder.clearMetadata();
            }
        });
    }

}
