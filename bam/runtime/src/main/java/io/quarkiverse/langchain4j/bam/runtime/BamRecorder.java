package io.quarkiverse.langchain4j.bam.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;
import java.util.function.Supplier;
import io.quarkiverse.langchain4j.bam.BamChatModel;
import io.quarkiverse.langchain4j.bam.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.Langchain4jBamConfig;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class BamRecorder {

    public Supplier<?> chatModel(Langchain4jBamConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();

        var builder = BamChatModel.builder()
                .accessToken(runtimeConfig.apiKey())
                .timeout(runtimeConfig.timeout())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())
                .modelId(chatModelConfig.modelId())
                .version(runtimeConfig.version())
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

        if (runtimeConfig.baseUrl().isPresent()) {
            builder.url(runtimeConfig.baseUrl().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }
}
