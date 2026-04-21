package io.quarkiverse.langchain4j.gpullama3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.beehive.gpullama3.model.format.ToolCallExtract;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

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

            // Use extractAllToolCalls to handle batched tool calls (matches ToolCallingSession)
            List<ToolCallExtract> toolCalls = holder.chatFormat.extractAllToolCalls(rawResponse);
            System.err.println("[GPU-DEBUG] extractAllToolCalls result: " + toolCalls.size() + " call(s)");
            if (!toolCalls.isEmpty()) {
                List<ToolExecutionRequest> toolReqs = new ArrayList<>();
                for (ToolCallExtract tc : toolCalls) {
                    LOG.infof("[Tool call]  → %s(%s)", tc.name(),
                            tc.argumentsJson().replace("\n", "").replaceAll("\\s+", " "));
                    toolReqs.add(ToolExecutionRequest.builder()
                            .name(tc.name())
                            .arguments(tc.argumentsJson())
                            .build());
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(toolReqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }

            // Plain text response — separate thinking content if present
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
