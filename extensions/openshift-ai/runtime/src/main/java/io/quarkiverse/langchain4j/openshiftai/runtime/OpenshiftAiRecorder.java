package io.quarkiverse.langchain4j.openshiftai.runtime;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.openshiftai.OpenshiftAiChatModel;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.Langchain4jOpenshiftAiConfig;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OpenshiftAiRecorder {

    public Supplier<?> chatModel(Langchain4jOpenshiftAiConfig runtimeConfig) {
        ChatModelConfig chatModelConfig = runtimeConfig.chatModel();

        var builder = OpenshiftAiChatModel.builder()
                .url(runtimeConfig.baseUrl())
                .timeout(runtimeConfig.timeout())
                .logRequests(runtimeConfig.logRequests())
                .logResponses(runtimeConfig.logResponses())

                .modelId(chatModelConfig.modelId());

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }
}
