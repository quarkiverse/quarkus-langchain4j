package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toOllamaMessages;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toTools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.smallrye.mutiny.Context;

/**
 * Use to have streaming feature on models used trough Ollama.
 */
public class OllamaStreamingChatLanguageModel implements StreamingChatLanguageModel {
    private static final String TOOLS_CONTEXT = "TOOLS";
    private static final String TOKEN_USAGE_CONTEXT = "TOKEN_USAGE";
    private static final String RESPONSE_CONTEXT = "RESPONSE";
    private final OllamaClient client;
    private final String model;
    private final String format;
    private final Options options;

    private OllamaStreamingChatLanguageModel(OllamaStreamingChatLanguageModel.Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses,
                builder.configName, builder.tlsConfigurationName);
        model = builder.model;
        format = builder.format;
        options = builder.options;
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
        context.put(RESPONSE_CONTEXT, new ArrayList<ChatResponse>());
        context.put(TOOLS_CONTEXT, new ArrayList<ToolExecutionRequest>());

        client.streamingChat(request)
                .subscribe()
                .with(context,
                        new Consumer<ChatResponse>() {
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
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable error) {
                                handler.onError(error);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {

                                TokenUsage tokenUsage = context.get(TOKEN_USAGE_CONTEXT);
                                List<ChatResponse> chatResponses = context.get(RESPONSE_CONTEXT);
                                List<ToolExecutionRequest> toolExecutionRequests = context.get(TOOLS_CONTEXT);

                                if (toolExecutionRequests.size() > 0) {
                                    handler.onComplete(Response.from(AiMessage.from(toolExecutionRequests), tokenUsage));
                                    return;
                                }

                                String response = chatResponses.stream()
                                        .map(ChatResponse::message)
                                        .map(Message::content)
                                        .collect(Collectors.joining());

                                AiMessage message = new AiMessage(response);
                                handler.onComplete(Response.from(message, tokenUsage));
                            }
                        });
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

        public OllamaStreamingChatLanguageModel build() {
            return new OllamaStreamingChatLanguageModel(this);
        }
    }

}
