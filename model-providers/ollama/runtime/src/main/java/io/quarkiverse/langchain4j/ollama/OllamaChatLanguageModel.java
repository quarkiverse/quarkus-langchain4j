package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toOllamaMessages;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toTools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public class OllamaChatLanguageModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final String model;
    private final String format;
    private final Options options;

    private OllamaChatLanguageModel(Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses,
                builder.configName);
        model = builder.model;
        format = builder.format;
        options = builder.options;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, Collections.emptyList());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages,
                toolSpecification != null ? Collections.singletonList(toolSpecification) : Collections.emptyList());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(toOllamaMessages(messages))
                .tools(toTools(toolSpecifications))
                .options(options)
                .format(format)
                .stream(false)
                .build();

        ChatResponse response = client.chat(request);

        List<ToolCall> toolCalls = response.message().toolCalls();
        if ((toolCalls == null) || toolCalls.isEmpty()) {
            return Response.from(
                    AiMessage.from(response.message().content()),
                    new TokenUsage(response.promptEvalCount(), response.evalCount()));
        } else {
            try {
                List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>(toolCalls.size());
                for (ToolCall toolCall : toolCalls) {
                    ToolCall.FunctionCall functionCall = toolCall.function();

                    // TODO: we need to update LangChain4j to make ToolExecutionRequest use a map instead of a String
                    String argumentsStr = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER
                            .writeValueAsString(functionCall.arguments());
                    toolExecutionRequests.add(ToolExecutionRequest.builder()
                            .name(functionCall.name())
                            .arguments(argumentsStr)
                            .build());
                }

                return Response.from(aiMessage(toolExecutionRequests),
                        new TokenUsage(response.promptEvalCount(), response.evalCount()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Unable to parse tool call response", e);
            }
        }
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:11434";
        private Duration timeout = Duration.ofSeconds(10);
        private String model;
        private String format;
        private Options options;

        private boolean logRequests = false;
        private boolean logResponses = false;
        private String configName;

        private Builder() {
        }

        public Builder baseUrl(String val) {
            baseUrl = val;
            return this;
        }

        public Builder timeout(Duration val) {
            this.timeout = val;
            return this;
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder format(String val) {
            format = val;
            return this;
        }

        public Builder options(Options val) {
            options = val;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public OllamaChatLanguageModel build() {
            return new OllamaChatLanguageModel(this);
        }
    }
}
