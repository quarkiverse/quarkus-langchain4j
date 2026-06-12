package io.quarkiverse.langchain4j.gpullama3;

import static io.quarkiverse.langchain4j.runtime.VertxUtil.runOutEventLoop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.beehive.gpullama3.model.format.ToolCallExtract;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

/**
 * GPULlama3StreamingChatModel is a specialized implementation of the {@link StreamingChatModel} for Quarkus-Langchain4j
 * extension.
 * It enables streaming mode for GPULlama3.java integration.
 * <p>
 * Considering that interoperability with GPU sometimes can be latency-prone, it operates in
 * an asynchronous, non-blocking manner, enabling efficient handling of conversational requests.
 * </p>
 *
 * <p>
 * Initialization:
 * </p>
 * <ul>
 * <li>The initialization of the model is performed in a background thread and marked complete using a future</li>
 * <li>If an inference request is made prior to initialization, it waits for the process to complete</li>
 * </ul>
 *
 * <p>
 * Response Generation:
 * </p>
 * <ul>
 * <li>Processes user inputs and generates model response</li>
 * <li>It is non-blocking as it is driven by a background thread</li>
 * <li>Delivers responses in a streaming format through registered handlers</li>
 * </ul>
 */
public class GPULlama3StreamingChatModel extends GPULlama3BaseModel implements StreamingChatModel {

    private static final Logger LOG = Logger.getLogger(GPULlama3StreamingChatModel.class);

    private GPULlama3StreamingChatModel(GPULlama3ModelHolder holder) {
        // no initialization here, it is done lazily by ensureInitialized() when first doChat() is called
        this.holder = holder;
    }

    public static GPULlama3StreamingChatModel create(GPULlama3ModelHolder holder) {
        return new GPULlama3StreamingChatModel(holder);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        // Run the GPU operations on a worker thread using runOutEventLoop
        runOutEventLoop(() -> {
            try {
                // Lazy initialization point: if not initialized yet, do it now
                holder.ensureInitialized();
                LOG.debug("Executing GPU Llama inference on worker thread");
                coreDoChat(chatRequest, handler);
            } catch (Exception e) {
                LOG.error("Failed during lazy initialization or inference", e);
                handler.onError(e);
            }
        });
    }

    /**
     * The actual doChat logic.
     * It is called by a worker thread in a non-blocking manner.
     */
    private void coreDoChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        try {
            // The StreamingParser detects and buffers tool-call JSON in real time (<tool_call> and
            // <|python_tag|> markers), so streaming is never suppressed — plain-text responses
            // stream token-by-token even when tool specifications are registered.
            GPULlama3ResponseParser.StreamingParser parser = GPULlama3ResponseParser.createStreamingParser(handler, getModel());

            String rawResponse = modelResponse(chatRequest, parser::onToken);

            // Finalize parser: resolves any unclosed <|python_tag|> tool call (LLaMA 3.1)
            List<ToolCallExtract> toolCalls = parser.finish();

            // Check for tool calls
            // Fallback for models that emit raw JSON without <tool_call> tags (rare)
            if (toolCalls.isEmpty()) {
                toolCalls = holder.chatFormat.extractAllToolCalls(rawResponse);
            }
            if (!toolCalls.isEmpty()) {
                LOG.infof("[LLM → tool call]\n%s", rawResponse.strip());
                String thinkingContent = parser.getThinkingContent();
                List<ToolExecutionRequest> toolReqs = new ArrayList<>();
                for (ToolCallExtract tc : toolCalls) {
                    String callId = tc.id().orElseGet(() -> generateCallId());
                    LOG.infof("[Tool call] → %s(%s)", tc.name(),
                            tc.argumentsJson().replace("\n", "").replaceAll("\\s+", " "));
                    toolReqs.add(ToolExecutionRequest.builder()
                            .id(callId)
                            .name(tc.name())
                            .arguments(tc.argumentsJson())
                            .build());
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .thinking(thinkingContent)
                                .toolExecutionRequests(toolReqs)
                                .build())
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
                return;
            }

            // Plain text — parse thinking and deliver final response
            GPULlama3ResponseParser.ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(rawResponse);

            LOG.infof("[LLM response]\n%s", parsed.getActualResponse());

            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .build());
        } catch (Exception e) {
            LOG.error("Error in GPULlama3 coreDoChat", e);
            handler.onError(e);
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
        private Boolean withPrefillDecode;
        private Integer prefillBatchSize;
        private Boolean enableThinking;

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

        public GPULlama3StreamingChatModel build() {
            GPULlama3ModelHolder h = modelHolder != null
                    ? modelHolder
                    : new GPULlama3ModelHolder(modelCachePath, modelName, quantization,
                            temperature, topP, seed, maxTokens, onGPU, withPrefillDecode, prefillBatchSize, enableThinking);
            return new GPULlama3StreamingChatModel(h);
        }
    }
}
