package io.quarkiverse.langchain4j.jitllm;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

public class JitLLMChatModel extends JitLLMBaseModel implements ChatModel {

    private static final Logger LOG = Logger.getLogger(JitLLMChatModel.class);

    private JitLLMChatModel(JitLLMModelHolder holder) {
        // no initialization here, it is done lazily by ensureInitialized() when first doChat() is called
        this.holder = holder;
    }

    public static JitLLMChatModel create(JitLLMModelHolder holder) {
        return new JitLLMChatModel(holder);
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

        boolean hasPriorToolResult = chatRequest.messages().stream()
                .anyMatch(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT);

        try {
            org.beehive.jitllm.api.GenerationResult result = modelResponse(chatRequest, null);
            String rawResponse = result.text();

            // Tool calls come from the engine now. It reports them only when a valid call was
            // extracted and generation ended through the format's tool-call termination path, so
            // tool-shaped text that did not parse arrives here as ordinary text -- which is what
            // it is.
            List<org.beehive.jitllm.api.ChatContent.ToolCall> toolCalls = result.toolCalls();
            LOG.debugf("tool calls: %d", toolCalls.size());
            if (!toolCalls.isEmpty()) {
                LOG.infof("[LLM → tool call]\n%s", rawResponse.strip());
                JitLLMResponseParser.ParsedResponse parsed = JitLLMResponseParser.parseResponse(rawResponse);
                LOG.debugf("[Parsed tool turn] toolCalls=%d  thinking=>>>%s<<<",
                        toolCalls.size(), parsed.getThinkingContent());
                List<ToolExecutionRequest> toolReqs = JitLLMConversions.toToolExecutionRequests(toolCalls);
                for (ToolExecutionRequest req : toolReqs) {
                    LOG.infof("[Tool call]  → %s(%s)", req.name(),
                            req.arguments().replace("\n", "").replaceAll("\\s+", " "));
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .thinking(parsed.getThinkingContent())
                                .toolExecutionRequests(toolReqs)
                                .build())
                        .finishReason(JitLLMConversions.toLangChain4jFinishReason(
                                result.finishReason()))
                        .tokenUsage(JitLLMConversions.toTokenUsage(result))
                        .build();
            }

            // Plain text response — separate thinking content if present
            JitLLMResponseParser.ParsedResponse parsed = JitLLMResponseParser.parseResponse(rawResponse);

            LOG.debugf("[Parsed response] thinking=>>>%s<<<", parsed.getThinkingContent());
            LOG.infof("[LLM response]\n%s", parsed.getActualResponse());

            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .finishReason(JitLLMConversions.toLangChain4jFinishReason(result.finishReason()))
                    .tokenUsage(JitLLMConversions.toTokenUsage(result))
                    .build();
        } catch (UnsupportedFeatureException e) {
            throw e; // a refusal of the request, not a generation failure
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate response from JitLLM", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private JitLLMModelHolder modelHolder;
        private Optional<Path> modelCachePath;
        private String modelName = Consts.DEFAULT_CHAT_MODEL_NAME;
        private String quantization = Consts.DEFAULT_CHAT_MODEL_QUANTIZATION;
        private Double temperature;
        private Double topP;
        private Integer seed;
        private Integer maxTokens;
        private Boolean onGPU;
        private Boolean withPrefillDecode;
        private Integer prefillBatchSize;
        private Boolean enableThinking;
        private String deviceMemory;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder modelHolder(JitLLMModelHolder modelHolder) {
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

        public Builder withPrefillDecode(Boolean withPrefillDecode) {
            this.withPrefillDecode = withPrefillDecode;
            return this;
        }

        public Builder prefillBatchSize(Integer prefillBatchSize) {
            this.prefillBatchSize = prefillBatchSize;
            return this;
        }

        public Builder enableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
            return this;
        }

        public Builder deviceMemory(String deviceMemory) {
            this.deviceMemory = deviceMemory;
            return this;
        }

        public JitLLMChatModel build() {
            JitLLMModelHolder h = modelHolder != null
                    ? modelHolder
                    : new JitLLMModelHolder(modelCachePath, modelName, quantization,
                            temperature, topP, seed, maxTokens, onGPU, withPrefillDecode, prefillBatchSize, enableThinking,
                            deviceMemory);
            return new JitLLMChatModel(h);
        }
    }
}
