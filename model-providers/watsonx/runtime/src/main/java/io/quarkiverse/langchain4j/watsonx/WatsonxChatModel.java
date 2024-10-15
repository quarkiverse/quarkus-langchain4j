package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.StreamingToolFetcher;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTools;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatResultChoice;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatResultMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextStreamingChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerTokenHeaderFactory;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Context;
import io.smallrye.mutiny.infrastructure.Infrastructure;

public class WatsonxChatModel implements ChatLanguageModel, StreamingChatLanguageModel, TokenCountEstimator {

    private static final String USAGE_CONTEXT = "USAGE";
    private static final String FINISH_REASON_CONTEXT = "FINISH_REASON";
    private static final String ROLE_CONTEXT = "ROLE";
    private static final String TOOLS_CONTEXT = "TOOLS";
    private static final String COMPLETE_MESSAGE_CONTEXT = "COMPLETE_MESSAGE";

    private final String modelId, projectId, version;
    private final WatsonxRestApi client;
    private final TextChatParameters parameters;

    public WatsonxChatModel(Builder builder) {

        QuarkusRestClientBuilder restClientBuilder = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(builder.url)
                .clientHeadersFactory(new BearerTokenHeaderFactory(builder.tokenGenerator))
                .connectTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(builder.timeout.toSeconds(), TimeUnit.SECONDS);

        if (builder.logRequests || builder.logResponses) {
            restClientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            restClientBuilder.clientLogger(new WatsonxRestApi.WatsonClientLogger(
                    builder.logRequests,
                    builder.logResponses));
        }

        this.client = restClientBuilder.build(WatsonxRestApi.class);
        this.modelId = builder.modelId;
        this.projectId = builder.projectId;
        this.version = builder.version;

        this.parameters = TextChatParameters.builder()
                .maxTokens(builder.maxTokens)
                .temperature(builder.temperature)
                .topP(builder.topP)
                .timeLimit(builder.timeout.toMillis())
                .responseFormat(builder.responseFormat)
                .build();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        var convertedMessages = messages.stream().map(TextChatMessage::convert).toList();
        var tools = (toolSpecifications != null && toolSpecifications.size() > 0)
                ? toolSpecifications.stream().map(TextChatParameterTools::of).toList()
                : null;

        TextChatRequest request = new TextChatRequest(modelId, projectId, convertedMessages, tools, parameters);

        TextChatResponse response = retryOn(new Callable<TextChatResponse>() {
            @Override
            public TextChatResponse call() throws Exception {
                return client.chat(request, version);
            }
        });

        TextChatResultChoice choice = response.choices().get(0);
        TextChatResultMessage message = choice.message();
        TextChatUsage usage = response.usage();

        AiMessage content;
        if (message.toolCalls() != null && message.toolCalls().size() > 0) {
            content = AiMessage.from(message.toolCalls().stream().map(TextChatToolCall::convert).toList());
        } else {
            content = AiMessage.from(message.content().trim());
        }

        var finishReason = toFinishReason(choice.finishReason());
        var tokenUsage = new TokenUsage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens());

        return Response.from(content, tokenUsage, finishReason);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
        var convertedMessages = messages.stream().map(TextChatMessage::convert).toList();
        var tools = (toolSpecifications != null && toolSpecifications.size() > 0)
                ? toolSpecifications.stream().map(TextChatParameterTools::of).toList()
                : null;

        TextChatRequest request = new TextChatRequest(modelId, projectId, convertedMessages, tools, parameters);
        Context context = Context.empty();
        context.put(TOOLS_CONTEXT, new ArrayList<StreamingToolFetcher>());
        context.put(COMPLETE_MESSAGE_CONTEXT, new StringBuilder());

        var mutiny = client.streamingChat(request, version);
        if (tools != null) {
            // Today Langchain4j doesn't allow to use the async operation with tools.
            // One idea might be to give to the developer the possibility to use the VirtualThread.
            mutiny.emitOn(Infrastructure.getDefaultWorkerPool());
        }

        mutiny.subscribe()
                .with(context,
                        new Consumer<TextStreamingChatResponse>() {
                            @Override
                            public void accept(TextStreamingChatResponse chunk) {
                                try {

                                    // Last message get the "usage" values
                                    if (chunk.choices().size() == 0) {
                                        context.put(USAGE_CONTEXT, chunk.usage());
                                        return;
                                    }

                                    var message = chunk.choices().get(0);

                                    if (message.finishReason() != null) {
                                        context.put(FINISH_REASON_CONTEXT, message.finishReason());
                                    }

                                    if (message.delta().role() != null) {
                                        context.put(ROLE_CONTEXT, message.delta().role());
                                    }

                                    if (message.delta().toolCalls() != null) {

                                        StreamingToolFetcher toolFetcher;

                                        // During streaming there is only one element in the tool_calls,
                                        // but the "index" field can be used to understand how many tools need to be executed.
                                        var deltaTool = message.delta().toolCalls().get(0);
                                        var index = deltaTool.index();

                                        List<StreamingToolFetcher> tools = context.get(TOOLS_CONTEXT);

                                        // Check if there is an incomplete version of the TextChatToolCall object.
                                        if ((index + 1) > tools.size()) {
                                            // First occurrence of the object, create it.
                                            toolFetcher = new StreamingToolFetcher(index);
                                            tools.add(toolFetcher);
                                        } else {
                                            // Incomplete version is present, complete it.
                                            toolFetcher = tools.get(index);
                                        }

                                        toolFetcher.setId(deltaTool.id());
                                        toolFetcher.setType(deltaTool.type());

                                        if (deltaTool.function() != null) {
                                            toolFetcher.setName(deltaTool.function().name());
                                            toolFetcher.appendArguments(deltaTool.function().arguments());
                                        }
                                    }

                                    if (message.delta().content() != null) {

                                        StringBuilder stringBuilder = context.get(COMPLETE_MESSAGE_CONTEXT);
                                        String token = message.delta().content();

                                        if (token.isEmpty())
                                            return;

                                        stringBuilder.append(token);
                                        handler.onNext(token);
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

                                TextStreamingChatResponse.TextChatUsage usage = context.get(USAGE_CONTEXT);
                                TokenUsage tokenUsage = new TokenUsage(
                                        usage.promptTokens(),
                                        usage.completionTokens(),
                                        usage.totalTokens());

                                String finishReason = context.get(FINISH_REASON_CONTEXT);
                                FinishReason finishReasonObj = toFinishReason(finishReason);

                                if (finishReason.equals("tool_calls")) {

                                    List<StreamingToolFetcher> tools = context.get(TOOLS_CONTEXT);
                                    List<ToolExecutionRequest> toolExecutionRequests = tools.stream()
                                            .map(StreamingToolFetcher::build).map(TextChatToolCall::convert).toList();

                                    handler.onComplete(
                                            Response.from(AiMessage.from(toolExecutionRequests), tokenUsage, finishReasonObj));

                                } else {

                                    StringBuilder message = context.get(COMPLETE_MESSAGE_CONTEXT);
                                    handler.onComplete(
                                            Response.from(AiMessage.from(message.toString()), tokenUsage, finishReasonObj));
                                }
                            }
                        });
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        var input = messages.stream().map(ChatMessage::text).collect(Collectors.joining());
        var request = new TokenizationRequest(modelId, input, projectId);

        return retryOn(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return client.tokenization(request, version).result().tokenCount();
            }
        });
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, List.of());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, List.of(toolSpecification));
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

    public static Builder builder() {
        return new Builder();
    }

    private FinishReason toFinishReason(String reason) {
        if (reason == null)
            return FinishReason.OTHER;

        return switch (reason) {
            case "length" -> FinishReason.LENGTH;
            case "stop" -> FinishReason.STOP;
            case "tool_calls" -> FinishReason.TOOL_EXECUTION;
            case "time_limit", "cancelled", "error" -> FinishReason.OTHER;
            default -> throw new IllegalArgumentException("%s not supported".formatted(reason));
        };
    }

    public static final class Builder {

        private String modelId;
        private String version;
        private String projectId;
        private Duration timeout;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private String responseFormat;
        private URL url;
        public boolean logResponses;
        public boolean logRequests;
        private WatsonxTokenGenerator tokenGenerator;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder tokenGenerator(WatsonxTokenGenerator tokenGenerator) {
            this.tokenGenerator = tokenGenerator;
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

        public WatsonxChatModel build() {
            return new WatsonxChatModel(this);
        }
    }
}
