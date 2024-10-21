package io.quarkiverse.langchain4j.jlama;

import static io.quarkiverse.langchain4j.jlama.JlamaModel.toFinishReason;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.prompt.PromptSupport;
import com.github.tjake.jlama.safetensors.prompt.Tool;
import com.github.tjake.jlama.safetensors.prompt.ToolCall;
import com.github.tjake.jlama.safetensors.prompt.ToolResult;
import com.github.tjake.jlama.util.JsonSupport;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class JlamaChatModel implements ChatLanguageModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;

    public JlamaChatModel(JlamaChatModelBuilder builder) {
        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(builder.modelCachePath);
        JlamaModel jlamaModel = RetryUtils
                .withRetry(() -> registry.downloadModel(builder.modelName, Optional.ofNullable(builder.authToken)), 3);

        JlamaModel.Loader loader = jlamaModel.loader();
        if (builder.quantizeModelAtRuntime != null && builder.quantizeModelAtRuntime)
            loader = loader.quantized();

        if (builder.workingQuantizedType != null)
            loader = loader.workingQuantizationType(builder.workingQuantizedType);

        if (builder.threadCount != null)
            loader = loader.threadCount(builder.threadCount);

        if (builder.workingDirectory != null)
            loader = loader.workingDirectory(builder.workingDirectory);

        this.model = loader.load();
        this.temperature = builder.temperature == null ? 0.3f : builder.temperature;
        this.maxTokens = builder.maxTokens == null ? model.getConfig().contextLength : builder.maxTokens;
    }

    public static JlamaChatModelBuilder builder() {
        return new JlamaChatModelBuilder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, List.of());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        if (model.promptSupport().isEmpty())
            throw new UnsupportedOperationException("This model does not support chat generation");

        PromptSupport.Builder promptBuilder = model.promptSupport().get().builder();

        for (ChatMessage message : messages) {
            switch (message.type()) {
                case SYSTEM -> promptBuilder.addSystemMessage(((SystemMessage) message).text());
                case USER -> {
                    StringBuilder finalMessage = new StringBuilder();
                    UserMessage userMessage = (UserMessage) message;
                    for (Content content : userMessage.contents()) {
                        if (content.type() != ContentType.TEXT)
                            throw new UnsupportedOperationException("Unsupported content type: " + content.type());

                        finalMessage.append(((TextContent) content).text());
                    }
                    promptBuilder.addUserMessage(finalMessage.toString());
                }
                case AI -> {
                    AiMessage aiMessage = (AiMessage) message;
                    if (aiMessage.text() != null)
                        promptBuilder.addAssistantMessage(aiMessage.text());

                    if (aiMessage.hasToolExecutionRequests())
                        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                            ToolCall toolCall = new ToolCall(toolExecutionRequest.name(), toolExecutionRequest.id(),
                                    Json.fromJson(toolExecutionRequest.arguments(), LinkedHashMap.class));
                            promptBuilder.addToolCall(toolCall);
                        }
                }
                case TOOL_EXECUTION_RESULT -> {
                    ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) message;
                    ToolResult result = ToolResult.from(toolMessage.toolName(), toolMessage.id(), toolMessage.text());
                    promptBuilder.addToolResult(result);
                }
                default -> throw new IllegalArgumentException("Unsupported message type: " + message.type());
            }
        }

        List<Tool> tools = toolSpecifications.stream().map(JlamaModel::toTool).toList();

        PromptContext promptContext = tools.isEmpty() ? promptBuilder.build() : promptBuilder.build(tools);
        Generator.Response r = model.generate(UUID.randomUUID(), promptContext, temperature, maxTokens, (token, time) -> {
        });

        if (r.finishReason == Generator.FinishReason.TOOL_CALL) {
            List<ToolExecutionRequest> toolCalls = r.toolCalls.stream().map(f -> ToolExecutionRequest.builder()
                    .name(f.getName())
                    .id(f.getId())
                    .arguments(JsonSupport.toJson(f.getParameters()))
                    .build()).toList();

            return Response.from(AiMessage.from(toolCalls), new TokenUsage(r.promptTokens, r.generatedTokens),
                    toFinishReason(r.finishReason));
        }

        return Response.from(AiMessage.from(r.responseText), new TokenUsage(r.promptTokens, r.generatedTokens),
                toFinishReason(r.finishReason));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, List.of(toolSpecification));
    }

    public static class JlamaChatModelBuilder {

        private Path modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private Path workingDirectory;
        private Boolean quantizeModelAtRuntime;
        private DType workingQuantizedType;
        private Float temperature;
        private Integer maxTokens;

        public JlamaChatModelBuilder modelCachePath(Path modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public JlamaChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JlamaChatModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public JlamaChatModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public JlamaChatModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaChatModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        public JlamaChatModelBuilder workingQuantizedType(DType workingQuantizedType) {
            this.workingQuantizedType = workingQuantizedType;
            return this;
        }

        public JlamaChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public JlamaChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public JlamaChatModel build() {
            return new JlamaChatModel(this);
        }
    }
}
