package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toOllamaMessages;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toTools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.smallrye.mutiny.Context;

/**
 * Use to have streaming feature on models used trough Ollama.
 */
public class OllamaStreamingChatLanguageModel implements StreamingChatLanguageModel {

    private static final Logger log = Logger.getLogger(OllamaStreamingChatLanguageModel.class);

    private static final String TOOLS_CONTEXT = "TOOLS";
    private static final String TOKEN_USAGE_CONTEXT = "TOKEN_USAGE";
    private static final String RESPONSE_CONTEXT = "RESPONSE";
    private static final String MODEL_ID = "MODEL_ID";
    private final OllamaClient client;
    private final String model;
    private final String format;
    private final Options options;
    private final List<ChatModelListener> listeners;

    private OllamaStreamingChatLanguageModel(OllamaStreamingChatLanguageModel.Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses,
                builder.configName, builder.tlsConfigurationName);
        model = builder.model;
        format = builder.format;
        options = builder.options;
        this.listeners = builder.listeners;
    }

    public static OllamaStreamingChatLanguageModel.Builder builder() {
        return new OllamaStreamingChatLanguageModel.Builder();
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");
        var tools = (toolSpecifications != null && toolSpecifications.size() > 0) ? toTools(toolSpecifications) : null;

        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(format)
                .tools(tools)
                .stream(true)
                .build();

        Context context = Context.empty();
        context.put(MODEL_ID, "");
        context.put(RESPONSE_CONTEXT, new ArrayList<ChatResponse>());
        context.put(TOOLS_CONTEXT, new ArrayList<ToolExecutionRequest>());

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

        client.streamingChat(request)
                .subscribe()
                .with(context,
                        new Consumer<>() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void accept(ChatResponse response) {
                                try {
                                    if ((response == null) || (response.message() == null)) {
                                        return;
                                    }

                                    if (response.message().toolCalls() != null) {
                                        List<ToolExecutionRequest> toolContext = context.get(TOOLS_CONTEXT);
                                        List<ToolCall> toolCalls = response.message().toolCalls();
                                        toolCalls.stream()
                                                .map(ToolCall::toToolExecutionRequest)
                                                .forEach(toolContext::add);
                                    }

                                    if (!response.message().content().isEmpty()) {
                                        ((List<ChatResponse>) context.get(RESPONSE_CONTEXT)).add(response);
                                        handler.onNext(response.message().content());
                                    }

                                    if (response.done()) {
                                        if (response.model() != null) {
                                            context.put(MODEL_ID, response.model());
                                        }
                                        TokenUsage tokenUsage = new TokenUsage(
                                                response.evalCount(),
                                                response.promptEvalCount(),
                                                response.evalCount() + response.promptEvalCount());
                                        context.put(TOKEN_USAGE_CONTEXT, tokenUsage);
                                    }

                                } catch (Exception e) {
                                    handler.onError(e);
                                }
                            }
                        },
                        new Consumer<>() {
                            @Override
                            public void accept(Throwable error) {
                                List<ChatResponse> chatResponses = context.get(RESPONSE_CONTEXT);
                                String stringResponse = chatResponses.stream()
                                        .map(ChatResponse::message)
                                        .map(Message::content)
                                        .collect(Collectors.joining());
                                AiMessage aiMessage = new AiMessage(stringResponse);
                                Response<AiMessage> aiMessageResponse = Response.from(aiMessage);

                                ChatModelResponse modelListenerPartialResponse = createModelListenerResponse(
                                        null,
                                        context.get(MODEL_ID),
                                        aiMessageResponse);

                                ChatModelErrorContext errorContext = new ChatModelErrorContext(
                                        error,
                                        modelListenerRequest,
                                        modelListenerPartialResponse,
                                        attributes);

                                listeners.forEach(listener -> {
                                    try {
                                        listener.onError(errorContext);
                                    } catch (Exception e) {
                                        log.warn("Exception while calling model listener", e);
                                    }
                                });

                                handler.onError(error);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {

                                TokenUsage tokenUsage = context.get(TOKEN_USAGE_CONTEXT);
                                List<ChatResponse> chatResponses = context.get(RESPONSE_CONTEXT);
                                List<ToolExecutionRequest> toolExecutionRequests = context.get(TOOLS_CONTEXT);

                                if (!toolExecutionRequests.isEmpty()) {
                                    handler.onComplete(Response.from(AiMessage.from(toolExecutionRequests), tokenUsage));
                                    return;
                                }

                                String stringResponse = chatResponses.stream()
                                        .map(ChatResponse::message)
                                        .map(Message::content)
                                        .collect(Collectors.joining());

                                AiMessage aiMessage = new AiMessage(stringResponse);
                                Response<AiMessage> aiMessageResponse = Response.from(aiMessage, tokenUsage);

                                ChatModelResponse modelListenerResponse = createModelListenerResponse(
                                        null,
                                        context.get(MODEL_ID),
                                        aiMessageResponse);
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

                                handler.onComplete(aiMessageResponse);
                            }
                        });
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

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification,
            StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(toolSpecification), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(), handler);
    }

    /**
     * Builder for Ollama configuration.
     */
    public static final class Builder {

        private Builder() {
            super();
        }

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

        public OllamaStreamingChatLanguageModel build() {
            return new OllamaStreamingChatLanguageModel(this);
        }
    }

}
