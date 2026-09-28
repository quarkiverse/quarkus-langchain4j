package io.quarkiverse.langchain4j.gpullama3;

import static io.quarkiverse.langchain4j.runtime.VertxUtil.runOutEventLoop;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

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
            // The StreamingParser detects and buffers tool-call text in real time (<tool_call>,
            // Gemma 4's <|tool_call>, <|python_tag|>, and Llama's bare JSON when tools are offered),
            // so streaming is never suppressed: plain-text responses stream token by token even when
            // tool specifications are registered.
            boolean toolsOffered = chatRequest.toolSpecifications() != null
                    && !chatRequest.toolSpecifications().isEmpty();
            GPULlama3ResponseParser.StreamingParser parser = GPULlama3ResponseParser.createStreamingParser(handler,
                    toolsOffered);

            // One ordered event per emitted completion token, carrying the id and the text it
            // completed. The parser needs only the text; the ids are there for consumers that do.
            org.beehive.jitllm.api.GenerationResult result = modelResponse(chatRequest, parser::onEvent);
            String rawResponse = result.text();
            parser.finish();

            // The engine's calls are the authoritative list: it validated them, and it reports
            // them only when generation ended through the format's tool-call termination path.
            List<org.beehive.jitllm.api.ChatContent.ToolCall> toolCalls = result.toolCalls();
            if (!toolCalls.isEmpty()) {
                LOG.infof("[LLM → tool call]\n%s", rawResponse.strip());
                String thinkingContent = parser.getThinkingContent();
                LOG.debugf("[Parsed tool turn] toolCalls=%d  thinking=>>>%s<<<", toolCalls.size(), thinkingContent);
                List<ToolExecutionRequest> toolReqs = GPULlama3Conversions.toToolExecutionRequests(toolCalls);
                for (ToolExecutionRequest req : toolReqs) {
                    LOG.infof("[Tool call] → %s(%s)", req.name(),
                            req.arguments().replace("\n", "").replaceAll("\\s+", " "));
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .thinking(thinkingContent)
                                .toolExecutionRequests(toolReqs)
                                .build())
                        .finishReason(GPULlama3Conversions.toLangChain4jFinishReason(
                                result.finishReason()))
                        .tokenUsage(GPULlama3Conversions.toTokenUsage(result))
                        .build());
                return;
            }

            // Plain text after all: stream anything the parser held back as a possible call
            parser.releaseHeldText();

            // Plain text — parse thinking and deliver final response
            GPULlama3ResponseParser.ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(rawResponse);

            LOG.debugf("[Parsed response] thinking=>>>%s<<<", parsed.getThinkingContent());
            LOG.infof("[LLM response]\n%s", parsed.getActualResponse());

            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .finishReason(GPULlama3Conversions.toLangChain4jFinishReason(result.finishReason()))
                    .tokenUsage(GPULlama3Conversions.toTokenUsage(result))
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
        private String deviceMemory;

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

        public Builder deviceMemory(String deviceMemory) {
            this.deviceMemory = deviceMemory;
            return this;
        }

        public GPULlama3StreamingChatModel build() {
            GPULlama3ModelHolder h = modelHolder != null
                    ? modelHolder
                    : new GPULlama3ModelHolder(modelCachePath, modelName, quantization,
                            temperature, topP, seed, maxTokens, onGPU, withPrefillDecode, prefillBatchSize, enableThinking,
                            deviceMemory);
            return new GPULlama3StreamingChatModel(h);
        }
    }
}
