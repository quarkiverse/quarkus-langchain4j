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
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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
    public void doChat(dev.langchain4j.model.chat.request.ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
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

        dev.langchain4j.model.chat.request.ChatRequest modelListenerRequest = createModelListenerRequest(request, messages,
                toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, ModelProvider.OTHER,
                attributes);
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
                                        handler.onPartialResponse(response.message().content());
                                    }

                                    if (response.done()) {
                                        if (response.model() != null) {
                                            context.put(MODEL_ID, response.model());
                                        }

                                        if (response.evalCount() != null && response.promptEvalCount() != null) {
                                            TokenUsage tokenUsage = new TokenUsage(
                                                    response.promptEvalCount(),
                                                    response.evalCount(),
                                                    response.evalCount() + response.promptEvalCount());
                                            context.put(TOKEN_USAGE_CONTEXT, tokenUsage);
                                        }
                                    }

                                } catch (Exception e) {
                                    handler.onError(e);
                                }
                            }
                        },
                        new Consumer<>() {
                            @Override
                            public void accept(Throwable error) {
                                ChatModelErrorContext errorContext = new ChatModelErrorContext(
                                        error,
                                        modelListenerRequest,
                                        ModelProvider.OTHER,
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

                                TokenUsage tokenUsage = context.contains(TOKEN_USAGE_CONTEXT) ? context.get(TOKEN_USAGE_CONTEXT)
                                        : null;
                                List<ChatResponse> chatResponses = context.get(RESPONSE_CONTEXT);
                                List<ToolExecutionRequest> toolExecutionRequests = context.get(TOOLS_CONTEXT);

                                if (!toolExecutionRequests.isEmpty()) {
                                    handler.onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse.builder()
                                            .aiMessage(AiMessage.from(toolExecutionRequests))
                                            .tokenUsage(tokenUsage).build());
                                    return;
                                }

                                String stringResponse = chatResponses.stream()
                                        .map(ChatResponse::message)
                                        .map(Message::content)
                                        .collect(Collectors.joining());

                                AiMessage aiMessage = new AiMessage(stringResponse);
                                dev.langchain4j.model.chat.response.ChatResponse aiMessageResponse = dev.langchain4j.model.chat.response.ChatResponse
                                        .builder().aiMessage(aiMessage).tokenUsage(tokenUsage).build();

                                dev.langchain4j.model.chat.response.ChatResponse modelListenerResponse = createModelListenerResponse(
                                        null,
                                        context.get(MODEL_ID),
                                        aiMessageResponse);
                                ChatModelResponseContext responseContext = new ChatModelResponseContext(
                                        modelListenerResponse,
                                        modelListenerRequest,
                                        ModelProvider.OTHER,
                                        attributes);
                                listeners.forEach(listener -> {
                                    try {
                                        listener.onResponse(responseContext);
                                    } catch (Exception e) {
                                        log.warn("Exception while calling model listener", e);
                                    }
                                });

                                handler.onCompleteResponse(aiMessageResponse);
                            }
                        });
    }

    private dev.langchain4j.model.chat.request.ChatRequest createModelListenerRequest(ChatRequest request,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        Options options = request.options();
        var requestParameters = ChatRequestParameters.builder()
                .modelName(request.model())
                .toolSpecifications(toolSpecifications);
        if (options != null) {
            requestParameters.temperature(options.temperature())
                    .topP(options.topP())
                    .maxOutputTokens(options.numPredict());
        }
        var builder = dev.langchain4j.model.chat.request.ChatRequest.builder()
                .messages(messages)
                .parameters(requestParameters.build());
        return builder.build();
    }

    private dev.langchain4j.model.chat.response.ChatResponse createModelListenerResponse(String responseId,
            String responseModel,
            dev.langchain4j.model.chat.response.ChatResponse aiMessageResponse) {
        if (aiMessageResponse == null) {
            return null;
        }

        return dev.langchain4j.model.chat.response.ChatResponse.builder()
                .finishReason(aiMessageResponse.finishReason())
                .aiMessage(aiMessageResponse.aiMessage())
                .metadata(ChatResponseMetadata.builder().id(responseId).modelName(responseModel)
                        .tokenUsage(aiMessageResponse.tokenUsage()).build())
                .build();
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
