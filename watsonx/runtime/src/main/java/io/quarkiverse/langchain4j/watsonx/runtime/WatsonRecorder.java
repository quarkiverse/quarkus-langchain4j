package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.watsonx.TokenGenerator;
import io.quarkiverse.langchain4j.watsonx.WatsonChatModel;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.Langchain4jWatsonConfig;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class WatsonRecorder {

    public Supplier<?> chatModel(Langchain4jWatsonConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();

        var builder = WatsonChatModel.builder()
                .url(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())
                .modelId(chatModelConfig.modelId())
                .version(runtimeConfig.version())
                .projectId(runtimeConfig.projectId())
                .decodingMethod(chatModelConfig.decodingMethod())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .temperature(chatModelConfig.temperature())
                .randomSeed(firstOrDefault(null, chatModelConfig.randomSeed()))
                .stopSequences(firstOrDefault(null, chatModelConfig.stopSequences()))
                .topK(firstOrDefault(null, chatModelConfig.topK()))
                .topP(firstOrDefault(null, chatModelConfig.topP()))
                .repetitionPenalty(firstOrDefault(null, chatModelConfig.repetitionPenalty()));

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> tokenGenerator(Langchain4jWatsonConfig runtimeConfig) {
        return new Supplier<>() {
            @Override
            public Object get() {
                return new TokenGenerator(
                        runtimeConfig.iam().baseUrl(),
                        runtimeConfig.iam().timeout(),
                        runtimeConfig.iam().grantType(),
                        runtimeConfig.apiKey());
            }
        };
    }
}
