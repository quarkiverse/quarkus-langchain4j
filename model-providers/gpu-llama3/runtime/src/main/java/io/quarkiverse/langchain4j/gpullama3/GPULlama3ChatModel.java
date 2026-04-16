package io.quarkiverse.langchain4j.gpullama3;

import java.nio.file.Path;
import java.util.Optional;

import org.jboss.logging.Logger;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

public class GPULlama3ChatModel extends GPULlama3BaseModel implements ChatModel {

    private static final Logger LOG = Logger.getLogger(GPULlama3ChatModel.class);

    private GPULlama3ChatModel(GPULlama3ModelHolder holder) {
        // no initialization here, it is done lazily by ensureInitialized() when first doChat() is called
        this.holder = holder;
    }

    public static GPULlama3ChatModel create(GPULlama3ModelHolder holder) {
        return new GPULlama3ChatModel(holder);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        // Lazy initialization point: if not initialized yet, do it now
        holder.ensureInitialized();

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private GPULlama3ModelHolder modelHolder;
        private Optional<Path> modelCachePath;
        private String modelName = Consts.DEFAULT_CHAT_MODEL_NAME;
        private String quantization = Consts.DEFAULT_CHAT_MODEL_QUANTIZATION;
        private Double temperature;
        private Double topP;
        private Integer seed;
        private Integer maxTokens;
        private Boolean onGPU;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder modelHolder(GPULlama3ModelHolder modelHolder) {
            this.modelHolder = modelHolder;
            return this;
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
            GPULlama3ModelHolder h = modelHolder != null
                    ? modelHolder
                    : new GPULlama3ModelHolder(modelCachePath, modelName, quantization,
                            temperature, topP, seed, maxTokens, onGPU);
            return new GPULlama3ChatModel(h);
        }
    }
}
