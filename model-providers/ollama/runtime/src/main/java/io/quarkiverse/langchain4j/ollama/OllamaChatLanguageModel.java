package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toOllamaMessages;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toTools;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class OllamaChatLanguageModel implements ChatLanguageModel {

    private static final Logger log = Logger.getLogger(OllamaChatLanguageModel.class);

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
            Response<AiMessage> response = toResponse(chatResponse);

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

            return response;
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

    private static Response<AiMessage> toResponse(ChatResponse response) {
        Response<AiMessage> result;
        List<ToolCall> toolCalls = response.message().toolCalls();
        if ((toolCalls == null) || toolCalls.isEmpty()) {
            result = Response.from(
                    AiMessage.from(response.message().content()),
                    new TokenUsage(response.promptEvalCount(), response.evalCount()));
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.stream().map(ToolCall::toToolExecutionRequest)
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
