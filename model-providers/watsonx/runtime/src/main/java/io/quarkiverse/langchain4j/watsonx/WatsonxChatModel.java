package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.StreamingToolFetcher;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTool;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatResultChoice;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatResultMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextStreamingChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.smallrye.mutiny.Context;

public class WatsonxChatModel extends Watsonx implements ChatLanguageModel, StreamingChatLanguageModel, TokenCountEstimator {

    private static final String ID_CONTEXT = "ID";
    private static final String MODEL_ID_CONTEXT = "MODEL_ID";
    private static final String USAGE_CONTEXT = "USAGE";
    private static final String FINISH_REASON_CONTEXT = "FINISH_REASON";
    private static final String ROLE_CONTEXT = "ROLE";
    private static final String TOOLS_CONTEXT = "TOOLS";
    private static final String COMPLETE_MESSAGE_CONTEXT = "COMPLETE_MESSAGE";

    private final WatsonxChatRequestParameters defaultRequestParameters;

    public WatsonxChatModel(Builder builder) {
        super(builder);

        //
        // The space_id and project_id fields cannot be overwritten by the ChatRequest object.
        //
        this.defaultRequestParameters = WatsonxChatRequestParameters.builder()
                .modelName(builder.modelId)
                .toolChoice(builder.toolChoice)
                .toolChoiceName(builder.toolChoiceName)
                .frequencyPenalty(builder.frequencyPenalty)
                // logit_bias cannot be set from application.properties, use it with the chat() method.
                .logprobs(builder.logprobs)
                .topLogprobs(builder.topLogprobs)
                .maxOutputTokens(builder.maxTokens)
                .n(builder.n)
                .presencePenalty(builder.presencePenalty)
                .responseFormat(builder.responseFormat)
                .seed(builder.seed)
                .stopSequences(builder.stop)
                .temperature(builder.temperature)
                .topP(builder.topP)
                .timeLimit(builder.timeout)
                .build();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        String modelId = chatRequest.parameters().modelName();
        ChatRequestParameters parameters = chatRequest.parameters();
        List<ToolSpecification> toolSpecifications = chatRequest.parameters().toolSpecifications();

        if (parameters.topK() != null)
            throw new UnsupportedFeatureException("'topK' parameter is not supported.");

        List<TextChatMessage> messages = chatRequest.messages().stream().map(TextChatMessage::convert).toList();
        List<TextChatParameterTool> tools = (toolSpecifications != null && toolSpecifications.size() > 0)
                ? toolSpecifications.stream().map(TextChatParameterTool::of).toList()
                : null;

        TextChatRequest request = new TextChatRequest(modelId, spaceId, projectId, messages, tools,
                TextChatParameters.convert(parameters));
        TextChatResponse response = retryOn(new Callable<TextChatResponse>() {
            @Override
            public TextChatResponse call() throws Exception {
                return client.chat(request, version);
            }
        });

        TextChatResultChoice choice = response.choices().get(0);
        TextChatResultMessage message = choice.message();
        TextChatUsage usage = response.usage();

        AiMessage aiMessage;
        if (message.toolCalls() != null && message.toolCalls().size() > 0) {
            aiMessage = AiMessage.from(message.toolCalls().stream().map(TextChatToolCall::convert).toList());
        } else {
            aiMessage = AiMessage.from(message.content().trim());
        }

        var finishReason = toFinishReason(choice.finishReason());
        var tokenUsage = new TokenUsage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens());

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .id(response.id())
                        .modelName(response.modelId())
                        .tokenUsage(tokenUsage)
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        String modelId = chatRequest.parameters().modelName();
        ChatRequestParameters parameters = chatRequest.parameters();
        List<ToolSpecification> toolSpecifications = chatRequest.parameters().toolSpecifications();

        if (parameters.topK() != null)
            throw new UnsupportedFeatureException("'topK' parameter is not supported.");

        List<TextChatMessage> messages = chatRequest.messages().stream().map(TextChatMessage::convert).toList();
        List<TextChatParameterTool> tools = (toolSpecifications != null && toolSpecifications.size() > 0)
                ? toolSpecifications.stream().map(TextChatParameterTool::of).toList()
                : null;

        TextChatRequest request = new TextChatRequest(modelId, spaceId, projectId, messages, tools,
                TextChatParameters.convert(parameters));
        Context context = Context.empty();
        context.put(TOOLS_CONTEXT, new ArrayList<StreamingToolFetcher>());
        context.put(COMPLETE_MESSAGE_CONTEXT, new StringBuilder());

        client.streamingChat(request, version)
                .onFailure(WatsonxUtils::isTokenExpired).retry().atMost(1)
                .subscribe()
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

                                    if (!context.contains(ID_CONTEXT) && chunk.id() != null) {
                                        context.put(ID_CONTEXT, chunk.id());
                                    }

                                    if (!context.contains(MODEL_ID_CONTEXT) && chunk.modelId() != null) {
                                        context.put(MODEL_ID_CONTEXT, chunk.modelId());
                                    }

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
                                        handler.onPartialResponse(token);
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

                                String id = context.get(ID_CONTEXT);
                                String modelId = context.get(MODEL_ID_CONTEXT);
                                String finishReason = context.get(FINISH_REASON_CONTEXT);
                                TextStreamingChatResponse.TextChatUsage textChatUsage = context.get(USAGE_CONTEXT);
                                TokenUsage tokenUsage = textChatUsage.toTokenUsage();

                                var chatResponse = ChatResponse.builder()
                                        .metadata(ChatResponseMetadata.builder()
                                                .id(id)
                                                .modelName(modelId)
                                                .tokenUsage(tokenUsage)
                                                .finishReason(toFinishReason(finishReason))
                                                .build());

                                if (finishReason.equals("tool_calls")) {

                                    List<StreamingToolFetcher> tools = context.get(TOOLS_CONTEXT);
                                    List<ToolExecutionRequest> toolExecutionRequests = tools.stream()
                                            .map(StreamingToolFetcher::build).map(TextChatToolCall::convert).toList();

                                    handler.onCompleteResponse(
                                            chatResponse.aiMessage(AiMessage.from(toolExecutionRequests)).build());

                                } else {

                                    StringBuilder message = context.get(COMPLETE_MESSAGE_CONTEXT);
                                    AiMessage aiMessage = AiMessage.from(message.toString());

                                    handler.onCompleteResponse(chatResponse.aiMessage(aiMessage).build());
                                }
                            }
                        });
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {

        var input = messages.stream().map(new Function<ChatMessage, String>() {
            @Override
            public String apply(ChatMessage message) {
                return switch (message.type()) {
                    case AI -> {
                        AiMessage aiMessage = (AiMessage) message;
                        yield aiMessage.hasToolExecutionRequests() ? "" : aiMessage.text();
                    }
                    case SYSTEM -> ((SystemMessage) message).text();
                    case USER -> ((UserMessage) message).singleText();
                    case CUSTOM, TOOL_EXECUTION_RESULT -> "";
                };
            }
        }).collect(joining(" "));

        var request = new TokenizationRequest(modelId, input, spaceId, projectId);

        return retryOn(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return client.tokenization(request, version).result().tokenCount();
            }
        });
    }

    @Override
    public List<ChatModelListener> listeners() {
        return super.listeners;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return this.defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        var parameters = WatsonxChatRequestParameters.builder()
                .toolSpecifications(toolSpecifications)
                .build();

        var chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(defaultRequestParameters().overrideWith(parameters))
                .build();

        var chatResponse = chat(chatRequest);
        return Response.from(chatResponse.aiMessage(), chatResponse.tokenUsage(), chatResponse.finishReason());
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
        var parameters = WatsonxChatRequestParameters.builder()
                .toolSpecifications(toolSpecifications)
                .build();

        var chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(defaultRequestParameters().overrideWith(parameters))
                .build();

        chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handler.onComplete(Response.from(completeResponse.aiMessage(), completeResponse.tokenUsage(),
                        completeResponse.finishReason()));
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
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

    public static final class Builder extends Watsonx.Builder<Builder> {

        private ToolChoice toolChoice;
        private String toolChoiceName;
        private Double frequencyPenalty;
        private Boolean logprobs;
        private Integer topLogprobs;
        private Integer maxTokens;
        private Integer n;
        private Double presencePenalty;
        private ResponseFormat responseFormat;
        private Integer seed;
        private List<String> stop;
        private Double temperature;
        private Double topP;

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
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

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public WatsonxChatModel build() {
            return new WatsonxChatModel(this);
        }
    }
}
