package io.quarkiverse.langchain4j.bam.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.bam.BamChatModel;
import io.quarkiverse.langchain4j.bam.BamEmbeddingModel;
import io.quarkiverse.langchain4j.bam.BamModel;
import io.quarkiverse.langchain4j.bam.BamModerationModel;
import io.quarkiverse.langchain4j.bam.BamStreamingChatModel;
import io.quarkiverse.langchain4j.bam.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class BamRecorder {

    private static final String DUMMY_KEY = "dummy";

    public Supplier<ChatLanguageModel> chatModel(LangChain4jBamConfig runtimeConfig, String modelName) {
        LangChain4jBamConfig.BamConfig bamConfig = correspondingBamConfig(runtimeConfig, modelName);

        if (bamConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = bamConfig.chatModel();
            String apiKey = bamConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
            }

            var builder = BamChatModel.builder()
                    .accessToken(bamConfig.apiKey())
                    .timeout(bamConfig.timeout())
                    .logRequests(bamConfig.logRequests())
                    .logResponses(bamConfig.logResponses())
                    .modelId(chatModelConfig.modelId())
                    .version(bamConfig.version())
                    .decodingMethod(chatModelConfig.decodingMethod())
                    .minNewTokens(chatModelConfig.minNewTokens())
                    .maxNewTokens(chatModelConfig.maxNewTokens())
                    .temperature(chatModelConfig.temperature())
                    .includeStopSequence(firstOrDefault(null, chatModelConfig.includeStopSequence()))
                    .randomSeed(firstOrDefault(null, chatModelConfig.randomSeed()))
                    .stopSequences(firstOrDefault(null, chatModelConfig.stopSequences()))
                    .timeLimit(firstOrDefault(null, chatModelConfig.timeLimit()))
                    .topK(firstOrDefault(null, chatModelConfig.topK()))
                    .topP(firstOrDefault(null, chatModelConfig.topP()))
                    .typicalP(firstOrDefault(null, chatModelConfig.typicalP()))
                    .repetitionPenalty(firstOrDefault(null, chatModelConfig.repetitionPenalty()))
                    .truncateInputTokens(firstOrDefault(null, chatModelConfig.truncateInputTokens()))
                    .beamWidth(firstOrDefault(null, chatModelConfig.beamWidth()));

            if (bamConfig.baseUrl().isPresent()) {
                builder.url(bamConfig.baseUrl().get());
            }

            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return builder.build(BamChatModel.class);
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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jBamConfig runtimeConfig, String modelName) {
        LangChain4jBamConfig.BamConfig bamConfig = correspondingBamConfig(runtimeConfig, modelName);

        if (bamConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = bamConfig.chatModel();
            String apiKey = bamConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
            }

            var builder = BamStreamingChatModel.builder()
                    .accessToken(bamConfig.apiKey())
                    .timeout(bamConfig.timeout())
                    .logRequests(bamConfig.logRequests())
                    .logResponses(bamConfig.logResponses())
                    .modelId(chatModelConfig.modelId())
                    .version(bamConfig.version())
                    .decodingMethod(chatModelConfig.decodingMethod())
                    .minNewTokens(chatModelConfig.minNewTokens())
                    .maxNewTokens(chatModelConfig.maxNewTokens())
                    .temperature(chatModelConfig.temperature())
                    .includeStopSequence(firstOrDefault(null, chatModelConfig.includeStopSequence()))
                    .randomSeed(firstOrDefault(null, chatModelConfig.randomSeed()))
                    .stopSequences(firstOrDefault(null, chatModelConfig.stopSequences()))
                    .timeLimit(firstOrDefault(null, chatModelConfig.timeLimit()))
                    .topK(firstOrDefault(null, chatModelConfig.topK()))
                    .topP(firstOrDefault(null, chatModelConfig.topP()))
                    .typicalP(firstOrDefault(null, chatModelConfig.typicalP()))
                    .repetitionPenalty(firstOrDefault(null, chatModelConfig.repetitionPenalty()))
                    .truncateInputTokens(firstOrDefault(null, chatModelConfig.truncateInputTokens()))
                    .beamWidth(firstOrDefault(null, chatModelConfig.beamWidth()));

            if (bamConfig.baseUrl().isPresent()) {
                builder.url(bamConfig.baseUrl().get());
            }

            return new Supplier<>() {
                @Override
                public StreamingChatLanguageModel get() {
                    return builder.build(BamStreamingChatModel.class);
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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jBamConfig runtimeConfig, String modelName) {
        LangChain4jBamConfig.BamConfig bamConfig = correspondingBamConfig(runtimeConfig, modelName);

        if (bamConfig.enableIntegration()) {
            EmbeddingModelConfig embeddingModelConfig = bamConfig.embeddingModel();
            String apiKey = bamConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
            }

            var builder = BamModel.builder()
                    .accessToken(bamConfig.apiKey())
                    .timeout(bamConfig.timeout())
                    .version(bamConfig.version())
                    .modelId(embeddingModelConfig.modelId())
                    .logRequests(bamConfig.logRequests())
                    .logResponses(bamConfig.logResponses());

            if (bamConfig.baseUrl().isPresent()) {
                builder.url(bamConfig.baseUrl().get());
            }

            return new Supplier<>() {
                @Override
                public EmbeddingModel get() {
                    return builder.build(BamEmbeddingModel.class);
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

    public Supplier<ModerationModel> moderationModel(LangChain4jBamConfig runtimeConfig, String modelName) {
        LangChain4jBamConfig.BamConfig bamConfig = correspondingBamConfig(runtimeConfig, modelName);

        if (bamConfig.enableIntegration()) {
            String apiKey = bamConfig.apiKey();
            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
            }

            ModerationModelConfig moderationModelConfig = bamConfig.moderationModel();
            var hap = moderationModelConfig.hap().orElse(null);
            var socialBias = moderationModelConfig.socialBias().orElse(null);

            var builder = BamModel.builder()
                    .accessToken(bamConfig.apiKey())
                    .timeout(bamConfig.timeout())
                    .version(bamConfig.version())
                    .messagesToModerate(moderationModelConfig.messagesToModerate())
                    .hap(hap)
                    .socialBias(socialBias)
                    .logRequests(bamConfig.logRequests())
                    .logResponses(bamConfig.logResponses());

            if (bamConfig.baseUrl().isPresent()) {
                builder.url(bamConfig.baseUrl().get());
            }

            return new Supplier<>() {
                @Override
                public ModerationModel get() {
                    return builder.build(BamModerationModel.class);
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

    private LangChain4jBamConfig.BamConfig correspondingBamConfig(LangChain4jBamConfig runtimeConfig, String modelName) {
        LangChain4jBamConfig.BamConfig bamConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            bamConfig = runtimeConfig.defaultConfig();
        } else {
            bamConfig = runtimeConfig.namedConfig().get(modelName);
        }
        return bamConfig;
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblem(String modelName) {
        return createConfigProblems("api-key", modelName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String modelName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, modelName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.bam%s%s is required but it could not be found in any config source",
                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
    }
}
