package io.quarkiverse.langchain4j.jlama;

import static io.quarkiverse.langchain4j.jlama.JlamaModel.toFinishReason;
import static io.quarkiverse.langchain4j.runtime.VertxUtil.runOutEventLoop;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.prompt.PromptSupport;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class JlamaStreamingChatModel implements StreamingChatLanguageModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;
    private final UUID id = UUID.randomUUID();

    public JlamaStreamingChatModel(JlamaStreamingChatModelBuilder builder) {
        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(builder.modelCachePath);
        JlamaModel jlamaModel = RetryUtils
                .withRetry(() -> registry.downloadModel(builder.modelName, Optional.ofNullable(builder.authToken)), 3);

        JlamaModel.Loader loader = jlamaModel.loader();
        if (builder.quantizeModelAtRuntime != null && builder.quantizeModelAtRuntime) {
            loader = loader.quantized();
        }

        if (builder.workingQuantizedType != null) {
            loader = loader.workingQuantizationType(builder.workingQuantizedType);
        }

        if (builder.threadCount != null) {
            loader = loader.threadCount(builder.threadCount);
        }

        if (builder.workingDirectory != null) {
            loader = loader.workingDirectory(builder.workingDirectory);
        }

        this.model = loader.load();
        this.temperature = builder.temperature == null ? 0.7f : builder.temperature;
        this.maxTokens = builder.maxTokens == null ? model.getConfig().contextLength : builder.maxTokens;
    }

    public static JlamaStreamingChatModelBuilder builder() {
        return new JlamaStreamingChatModelBuilder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        PromptContext promptContext = createPromptContext(chatRequest.messages());
        runOutEventLoop(new Runnable() {
            @Override
            public void run() {
                internalGenerate(handler, promptContext);
            }
        });
    }

    private void internalGenerate(StreamingChatResponseHandler handler, PromptContext promptContext) {
        try {
            Generator.Response r = model.generate(id, promptContext, temperature, maxTokens, (token, time) -> {
                handler.onPartialResponse(token);
            });

            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(r.responseText))
                    .tokenUsage(new TokenUsage(r.promptTokens, r.generatedTokens))
                    .finishReason(toFinishReason(r.finishReason))
                    .build());
        } catch (Throwable t) {
            handler.onError(t);
        }
    }

    private PromptContext createPromptContext(List<ChatMessage> messages) {
        if (model.promptSupport().isEmpty()) {
            throw new UnsupportedOperationException("This model does not support chat generation");
        }

        PromptSupport.Builder promptBuilder = model.promptSupport().get().builder();
        for (ChatMessage message : messages) {
            switch (message.type()) {
                case SYSTEM -> promptBuilder.addSystemMessage(message.text());
                case USER -> promptBuilder.addUserMessage(message.text());
                case AI -> promptBuilder.addAssistantMessage(message.text());
                default -> throw new IllegalArgumentException("Unsupported message type: " + message.type());
            }
        }
        return promptBuilder.build();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class JlamaStreamingChatModelBuilder {

        private Optional<Path> modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private Path workingDirectory;
        private Boolean quantizeModelAtRuntime;
        private DType workingQuantizedType;
        private Float temperature;
        private Integer maxTokens;

        public JlamaStreamingChatModelBuilder modelCachePath(Optional<Path> modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public JlamaStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JlamaStreamingChatModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public JlamaStreamingChatModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public JlamaStreamingChatModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaStreamingChatModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        public JlamaStreamingChatModelBuilder workingQuantizedType(DType workingQuantizedType) {
            this.workingQuantizedType = workingQuantizedType;
            return this;
        }

        public JlamaStreamingChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public JlamaStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public JlamaStreamingChatModel build() {
            return new JlamaStreamingChatModel(this);
        }
    }
}
