package io.quarkiverse.langchain4j.gpullama3;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

public class GPULlama3ChatModel extends GPULlama3BaseModel implements ChatModel {

    // @formatter:off
    private GPULlama3ChatModel(Builder builder) {
        GPULlama3ModelRegistry gpuLlama3ModelRegistry = GPULlama3ModelRegistry.getOrCreate(builder.modelCachePath);
        try {
            Path modelPath = gpuLlama3ModelRegistry.downloadModel(builder.modelName, builder.quantization,
                    Optional.empty(), Optional.empty());
            init(
                    modelPath,
                    getOrDefault(builder.temperature, 0.1),
                    getOrDefault(builder.topP, 1.0),
                    getOrDefault(builder.seed, 12345),
                    getOrDefault(builder.maxTokens, 512),
                    getOrDefault(builder.onGPU, Boolean.TRUE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    // @formatter:on

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        try {
            // Generate a raw response from the model
            String rawResponse = modelResponse(chatRequest, null);

            // Parse thinking and actual response using the GPULlama3ResponseParser
            GPULlama3ResponseParser.ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(rawResponse);

            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate response from GPULlama3", e);
        }
    }

    public static class Builder {

        private Optional<Path> modelCachePath;
        private String modelName = Consts.DEFAULT_CHAT_MODEL_NAME;
        private String quantization = Consts.DEFAULT_CHAT_MODEL_QUANTIZATION;
        protected Double temperature;
        protected Double topP;
        protected Integer seed;
        protected Integer maxTokens;
        protected Boolean onGPU;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder modelCachePath(Optional<Path> modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder quantization(String quantization) {
            this.quantization = quantization;
            return this;
        }

        public Builder onGPU(Boolean onGPU) {
            this.onGPU = onGPU;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public GPULlama3ChatModel build() {
            return new GPULlama3ChatModel(this);
        }
    }
}
