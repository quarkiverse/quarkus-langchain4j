package io.quarkiverse.langchain4j.runtime;

import java.util.Optional;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiLanguageModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingLanguageModel;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.config.ModelProvider;
import io.quarkiverse.langchain4j.runtime.config.OpenAiChatParams;
import io.quarkiverse.langchain4j.runtime.config.OpenAiEmbeddingParams;
import io.quarkiverse.langchain4j.runtime.config.OpenAiModerationParams;
import io.quarkiverse.langchain4j.runtime.config.OpenAiServer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class Langchain4jModelsRecorder {

    public RuntimeValue<?> chatModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
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
                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature())
                    .topP(openAiChatParams.topP())
                    .maxTokens(openAiChatParams.maxTokens())
                    .presencePenalty(openAiChatParams.presencePenalty())
                    .frequencyPenalty(openAiChatParams.frequencyPenalty())
                    .timeout(openAiChatParams.timeout())
                    .maxRetries(openAiServer.maxRetries())
                    .logRequests(openAiServer.logRequests())
                    .logResponses(openAiServer.logResponses());

            return new RuntimeValue<>(builder.build());
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

    public RuntimeValue<?> streamingChatModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
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
                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature())
                    .topP(openAiChatParams.topP())
                    .maxTokens(openAiChatParams.maxTokens())
                    .presencePenalty(openAiChatParams.presencePenalty())
                    .frequencyPenalty(openAiChatParams.frequencyPenalty())
                    .timeout(openAiServer.timeout())
                    .logRequests(openAiServer.logRequests())
                    .logResponses(openAiServer.logResponses());

            return new RuntimeValue<>(builder.build());
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

    public RuntimeValue<?> languageModel(ModelProvider modelProvider,
            LangChain4jRuntimeConfig runtimeConfig) {
        if (modelProvider == ModelProvider.OPEN_AI) {
            OpenAiServer openAi = runtimeConfig.openAi();
            Optional<String> apiKeyOpt = openAi.apiKey();
            if (apiKeyOpt.isEmpty()) {
                throw new ConfigValidationException(createProblems("openai", "api-key"));
            }
            OpenAiChatParams openAiChatParams = runtimeConfig.chatModel().openAi();
            var builder = OpenAiLanguageModel.builder()
                    .baseUrl(openAi.baseUrl())
                    .apiKey(apiKeyOpt.get())
                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature())
                    .timeout(openAi.timeout())
                    .maxRetries(openAi.maxRetries())
                    .logRequests(openAi.logRequests())
                    .logResponses(openAi.logResponses());

            return new RuntimeValue<>(builder.build());
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

    public RuntimeValue<?> streamingLanguageModel(ModelProvider modelProvider,
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
                    .modelName(openAiChatParams.modelName())
                    .temperature(openAiChatParams.temperature())
                    .timeout(openAi.timeout())
                    .logRequests(openAi.logRequests())
                    .logResponses(openAi.logResponses());

            return new RuntimeValue<>(builder.build());
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

    public RuntimeValue<?> embeddingModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
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
                    .modelName(modelParams.modelName())
                    .timeout(openAi.timeout())
                    .maxRetries(openAi.maxRetries())
                    .logRequests(openAi.logRequests())
                    .logResponses(openAi.logResponses());

            return new RuntimeValue<>(builder.build());
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

    public RuntimeValue<?> moderationModel(ModelProvider modelProvider, LangChain4jRuntimeConfig runtimeConfig) {
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
                    .modelName(params.modelName())
                    .timeout(openAi.timeout())
                    .maxRetries(openAi.maxRetries())
                    .logRequests(openAi.logRequests())
                    .logResponses(openAi.logResponses());

            return new RuntimeValue<>(builder.build());
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
}
