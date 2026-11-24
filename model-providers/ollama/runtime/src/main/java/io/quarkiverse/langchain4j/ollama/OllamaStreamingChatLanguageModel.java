package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toOllamaMessages;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toTools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import io.smallrye.mutiny.Context;

/**
 * Use to have streaming feature on models used trough Ollama.
 */
public class OllamaStreamingChatLanguageModel implements StreamingChatModel {

    private static final String TOOLS_CONTEXT = "TOOLS";
    private static final String TOKEN_USAGE_CONTEXT = "TOKEN_USAGE";
    private static final String RESPONSE_CONTEXT = "RESPONSE";
    private static final String THINKING_CONTEXT = "THINKING";
    private static final String MODEL_ID = "MODEL_ID";
    private final OllamaClient client;
    private final String model;
    private final String format;
    private final Options options;
    private final Boolean think;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;

    private OllamaStreamingChatLanguageModel(OllamaStreamingChatLanguageModel.Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses,
                builder.logCurl, builder.configName, builder.tlsConfigurationName);
        model = builder.model;
        format = builder.format;
        options = builder.options;
        think = builder.think;
        this.listeners = builder.listeners;
        var parameters = ChatRequestParameters.builder().modelName(model);
        if (options != null) {
            parameters.temperature(options.temperature())
                    .topK(options.topK())
                    .topP(options.topP())
                    .maxOutputTokens(options.numPredict());
        }
        this.defaultRequestParameters = parameters.build();
    }

    public static OllamaStreamingChatLanguageModel.Builder builder() {
        return new OllamaStreamingChatLanguageModel.Builder();
    }

    /**
     * Exposes the configured listeners to the langchain4j {@code chat(...)} default methods, which take care of
     * notifying them (with the current invocation id, the per-call listener attributes and any globally
     * registered observability listeners) around {@code doChat(...)}.
     */
    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    /**
     * The model-level parameters, merged under the per-request ones by the langchain4j {@code chat(...)} default
     * methods before {@code doChat(...)} is called, so listeners see the effective request.
     */
    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OLLAMA;
    }

    @Override
    public void doChat(dev.langchain4j.model.chat.request.ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        try {
            streamingChat(chatRequest, handler);
        } catch (RuntimeException e) {
            // the default chat(...) has already notified the listeners of the request, so a failure before the
            // request is sent has to reach them (and the caller) through the handler
            handler.onError(e);
        }
    }

    private void streamingChat(dev.langchain4j.model.chat.request.ChatRequest chatRequest,
            StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        ensureNotEmpty(messages, "messages");
        var tools = (toolSpecifications != null && toolSpecifications.size() > 0) ? toTools(toolSpecifications) : null;

        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(format)
                .think(think)
                .tools(tools)
                .stream(true)
                .build();

        Context context = Context.empty();
        context.put(MODEL_ID, "");
        context.put(RESPONSE_CONTEXT, new ArrayList<ChatResponse>());
        context.put(THINKING_CONTEXT, new ArrayList<String>());
        context.put(TOOLS_CONTEXT, new ArrayList<ToolExecutionRequest>());

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

                                    String thinking = response.message().thinking();
                                    if (thinking != null && !thinking.isEmpty()) {
                                        ((List<String>) context.get(THINKING_CONTEXT)).add(thinking);
                                        handler.onPartialThinking(new PartialThinking(thinking));
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
                                handler.onError(error);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {

                                TokenUsage tokenUsage = context.contains(TOKEN_USAGE_CONTEXT) ? context.get(TOKEN_USAGE_CONTEXT)
                                        : null;
                                String modelId = context.get(MODEL_ID);
                                String modelName = modelId.isEmpty() ? null : modelId;
                                List<ChatResponse> chatResponses = context.get(RESPONSE_CONTEXT);
                                List<String> thinkingResponses = context.get(THINKING_CONTEXT);
                                List<ToolExecutionRequest> toolExecutionRequests = context.get(TOOLS_CONTEXT);

                                String thinkingResponse = String.join("", thinkingResponses);

                                if (!toolExecutionRequests.isEmpty()) {
                                    handler.onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse.builder()
                                            .aiMessage(AiMessage.builder()
                                                    .thinking(thinkingResponse.isEmpty() ? null : thinkingResponse)
                                                    .toolExecutionRequests(toolExecutionRequests)
                                                    .build())
                                            .modelName(modelName)
                                            .tokenUsage(tokenUsage)
                                            .build());
                                    return;
                                }

                                String stringResponse = chatResponses.stream()
                                        .map(ChatResponse::message)
                                        .map(Message::content)
                                        .collect(Collectors.joining());

                                AiMessage aiMessage = AiMessage.builder()
                                        .text(stringResponse)
                                        .thinking(thinkingResponse.isEmpty() ? null : thinkingResponse)
                                        .build();
                                handler.onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse.builder()
                                        .aiMessage(aiMessage)
                                        .modelName(modelName)
                                        .tokenUsage(tokenUsage)
                                        .build());
                            }
                        });
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
        private Boolean think;

        private boolean logRequests = false;
        private boolean logResponses = false;
        private boolean logCurl = false;
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

        public Builder think(Boolean val) {
            think = val;
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

        public Builder logCurl(boolean logCurl) {
            this.logCurl = logCurl;
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
