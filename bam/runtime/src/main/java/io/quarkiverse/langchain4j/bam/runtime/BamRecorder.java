package io.quarkiverse.langchain4j.bam.runtime;

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
                .version(chatModelConfig.version())
                .decodingMethod(chatModelConfig.decodingMethod())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .temperature(chatModelConfig.temperature());

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
