package io.quarkiverse.langchain4j.gpullama3;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static io.quarkiverse.langchain4j.runtime.VertxUtil.runOutEventLoop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

import org.jboss.logging.Logger;

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

    private final Builder builderConfig;
    private volatile boolean initialized = false;

    private GPULlama3StreamingChatModel(Builder builder, boolean lazy) {
        if (lazy) {
            this.builderConfig = builder;
            // Don't initialize yet!
        } else {
            this.builderConfig = null;
            // Original background initialization
            runOutEventLoop(() -> {
                LOG.debug("Starting GPULlama3 StreamingChatModel initialization on worker thread");
                doInitialization(builder);
                initialized = true;
            });
        }
    }

    private GPULlama3StreamingChatModel(Builder builder) {
        this(builder, false); // Default to original background initialization
    }

    // Add factory method for lazy initialization
    public static GPULlama3StreamingChatModel createLazy(Builder builder) {
        return new GPULlama3StreamingChatModel(builder, true);
    }

    private void ensureInitialized() {
        if (!initialized && builderConfig != null) {
            if (!initialized) {
                LOG.debug("Lazy initialization of GPULlama3StreamingChatModel");
                doInitialization(builderConfig);
                initialized = true;
            }
        }
    }

    private void doInitialization(Builder builder) {
        GPULlama3ModelRegistry gpuLlama3ModelRegistry = GPULlama3ModelRegistry.getOrCreate(builder.modelCachePath);
        try {
            Path modelPath = gpuLlama3ModelRegistry.downloadModel(builder.modelName, builder.quantization,
                    Optional.empty(), Optional.empty());
            Double temp = getOrDefault(builder.temperature, 0.1);
            Double topP = getOrDefault(builder.topP, 1.0);
            Integer seed = getOrDefault(builder.seed, 12345);
            Integer maxTokens = getOrDefault(builder.maxTokens, 512);
            Boolean onGPU = getOrDefault(builder.onGPU, Boolean.TRUE);

            LOG.info("GPULlama3StreamingChatModel Instantiation {modelPath=" + modelPath +
                    ", temperature=" + temp +
                    ", topP=" + topP +
                    ", seed=" + seed +
                    ", maxTokens=" + maxTokens +
                    ", onGPU=" + onGPU + "}...");

            init(modelPath, temp, topP, seed, maxTokens, onGPU);
            LOG.info("GPULlama3StreamingChatModel Instantiation Complete!");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
                ensureInitialized(); // Build happens HERE on first call!
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
            // Create streaming parser using the utility class
            GPULlama3ResponseParser.StreamingParser parser = GPULlama3ResponseParser.createStreamingParser(handler, getModel());

            // Generate response with streaming callback
            String rawResponse = modelResponse(chatRequest, parser::onToken);

            // Parse the complete response and send final result
            GPULlama3ResponseParser.ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(rawResponse);

            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .build();

            handler.onCompleteResponse(chatResponse);
        } catch (Exception e) {
            LOG.error("Error in GPULlama3 coreDoChat", e);
            handler.onError(e);
        }
    }

    public static Builder builder() {
        return new Builder();
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

        public GPULlama3StreamingChatModel build() {
            return new GPULlama3StreamingChatModel(this);
        }
    }
}
