package io.quarkiverse.langchain4j.bam.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.bam.BamChatModel;
import io.quarkiverse.langchain4j.bam.BamEmbeddingModel;
import io.quarkiverse.langchain4j.bam.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.Langchain4jBamConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class BamRecorder {

    private static final String DUMMY_KEY = "dummy";

    public Supplier<?> chatModel(Langchain4jBamConfig runtimeConfig, String modelName) {
        Langchain4jBamConfig.BamConfig bamConfig = correspondingBamConfig(runtimeConfig, modelName);
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
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> embeddingModel(Langchain4jBamConfig runtimeConfig, String modelName) {
        Langchain4jBamConfig.BamConfig bamConfig = correspondingBamConfig(runtimeConfig, modelName);
        EmbeddingModelConfig embeddingModelConfig = bamConfig.embeddingModel();
        String apiKey = bamConfig.apiKey();
        if (DUMMY_KEY.equals(apiKey)) {
            throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
        }

        var builder = BamEmbeddingModel.builder()
                .accessToken(bamConfig.apiKey())
                .timeout(bamConfig.timeout())
                .version(bamConfig.version())
                .modelId(embeddingModelConfig.modelId());

        if (bamConfig.baseUrl().isPresent()) {
            builder.url(bamConfig.baseUrl().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private Langchain4jBamConfig.BamConfig correspondingBamConfig(Langchain4jBamConfig runtimeConfig, String modelName) {
        Langchain4jBamConfig.BamConfig bamConfig;
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
