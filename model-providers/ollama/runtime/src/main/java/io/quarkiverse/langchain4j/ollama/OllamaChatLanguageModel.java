package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toOllamaMessages;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toTools;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public class OllamaChatLanguageModel implements ChatLanguageModel {

    private static final Logger log = Logger.getLogger(OllamaChatLanguageModel.class);
    private static final ObjectMapper objectMapper = QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;

    private final OllamaClient client;
    private final String model;
    private final String format;
    private final Options options;
    private final List<ChatModelListener> listeners;

    private OllamaChatLanguageModel(Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses,
                builder.configName, builder.tlsConfigurationName);
        model = builder.model;
        format = builder.format;
        options = builder.options;
        listeners = builder.listeners;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public dev.langchain4j.model.chat.response.ChatResponse doChat(dev.langchain4j.model.chat.request.ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        ResponseFormat responseFormat = chatRequest.responseFormat();

        ensureNotEmpty(messages, "messages");

        ChatRequest.Builder builder = ChatRequest.builder()
                .model(model)
                .messages(toOllamaMessages(messages))
                .tools(toolSpecifications == null ? null : toTools(toolSpecifications))
                .options(options)
                .stream(false);

        if (format != null && !format.isBlank()) {
            // If the developer specifies something in the "format" property, it has high priority.
            builder.format(format);
        } else if (responseFormat != null && responseFormat.type().equals(JSON)) {
            try {
                var jsonSchema = JsonSchemaElementHelper.toMap(responseFormat.jsonSchema().rootElement());
                builder.format(objectMapper.writeValueAsString(jsonSchema));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ChatRequest request = builder.build();
        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        try {
            ChatResponse chatResponse = client.chat(request);
            Response<AiMessage> response = toResponse(chatResponse, toolSpecifications);

            ChatModelResponse modelListenerResponse = createModelListenerResponse(
                    null,
                    chatResponse.model(),
                    response);
            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                    modelListenerResponse,
                    modelListenerRequest,
                    attributes);
            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(response.content())
                    .finishReason(response.finishReason())
                    .build();

        } catch (RuntimeException e) {
            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    e,
                    modelListenerRequest,
                    null,
                    attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });

            throw e;
        }
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        if (format == null || !format.equalsIgnoreCase("json"))
            return Set.of(RESPONSE_FORMAT_JSON_SCHEMA);
        return Set.of();
    }

    private static Response<AiMessage> toResponse(ChatResponse response, List<ToolSpecification> toolSpecifications) {
        Response<AiMessage> result;
        List<ToolCall> toolCalls = response.message().toolCalls();
        if ((toolCalls == null) || toolCalls.isEmpty()) {
            result = Response.from(
                    AiMessage.from(response.message().content()),
                    new TokenUsage(response.promptEvalCount(), response.evalCount()));
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.stream()
                    .filter(createToolSpecificationChecker(toolSpecifications))
                    .map(ToolCall::toToolExecutionRequest)
                    .toList();

            result = Response.from(aiMessage(toolExecutionRequests),
                    new TokenUsage(response.promptEvalCount(), response.evalCount()));
        }
        return result;
    }

    private ChatModelRequest createModelListenerRequest(ChatRequest request,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        Options options = request.options();
        var builder = ChatModelRequest.builder()
                .model(request.model())
                .messages(messages)
                .toolSpecifications(toolSpecifications);
        if (options != null) {
            builder.temperature(options.temperature())
                    .topP(options.topP())
                    .maxTokens(options.numPredict());
        }
        return builder.build();
    }

    private ChatModelResponse createModelListenerResponse(String responseId,
            String responseModel,
            Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }

    private static Predicate<? super ToolCall> createToolSpecificationChecker(
            List<ToolSpecification> toolSpecifications) {
        return toolCall -> toolSpecifications.stream()
                .anyMatch(toolSpecification -> {
                    if (toolSpecification.name().equals(toolCall.function().name())) {
                        return true;
                    }
                    log.infov("Model tried to call tool {0} which is not present",
                            toolCall.function().name());
                    return false;
                });
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:11434";
        private String tlsConfigurationName;
        private Duration timeout = Duration.ofSeconds(10);
        private String model;
        private String format;
        private Options options;

        private boolean logRequests = false;
        private boolean logResponses = false;
        private String configName;
        private List<ChatModelListener> listeners = Collections.emptyList();

        private Builder() {
        }

        public Builder baseUrl(String val) {
            baseUrl = val;
            return this;
        }

        public Builder tlsConfigurationName(String tlsConfigurationName) {
            this.tlsConfigurationName = tlsConfigurationName;
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

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OllamaChatLanguageModel build() {
            return new OllamaChatLanguageModel(this);
        }
    }
}
