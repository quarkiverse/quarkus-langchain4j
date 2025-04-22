package io.quarkiverse.langchain4j.watsonx;

import static java.util.Objects.nonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.StreamingToolFetcher;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTool;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextStreamingChatResponse;
import io.smallrye.mutiny.Context;

public class WatsonxStreamingChatModel extends Watsonx implements StreamingChatLanguageModel {

    private static final String ID_CONTEXT = "ID";
    private static final String MODEL_ID_CONTEXT = "MODEL_ID";
    private static final String USAGE_CONTEXT = "USAGE";
    private static final String FINISH_REASON_CONTEXT = "FINISH_REASON";
    private static final String ROLE_CONTEXT = "ROLE";
    private static final String TOOLS_CONTEXT = "TOOLS";
    private static final String COMPLETE_MESSAGE_CONTEXT = "COMPLETE_MESSAGE";

    private final WatsonxChatRequestParameters defaultRequestParameters;

    public WatsonxStreamingChatModel(Builder builder) {
        super(builder);

        //
        // The space_id and project_id fields cannot be overwritten by the ChatRequest
        // object.
        //
        this.defaultRequestParameters = WatsonxChatRequestParameters.builder()
                .modelName(builder.modelId)
                .toolChoice(builder.toolChoice)
                .toolChoiceName(builder.toolChoiceName)
                .frequencyPenalty(builder.frequencyPenalty)
                // logit_bias cannot be set from application.properties, use it with the chat()
                // method.
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

        TextChatParameters textChatParameters = TextChatParameters.convert(parameters);
        Context context = Context.empty();

        if (nonNull(parameters.toolChoice()) && parameters.toolChoice().equals(ToolChoice.REQUIRED)) {
            // This code is needed to avoid a infinite-loop when using the AiService
            // in combination with the tool-choice option.
            // If the tool-choice option is not removed after calling the tool,
            // the model may continuously reselect the same tool in subsequent responses,
            // even though the tool has already been invoked. This leads to an infinite loop
            // where the assistant keeps generating tool calls without progressing the
            // conversation.
            // By explicitly removing the tool-choice field after the tool has been
            // executed,
            // we allow the assistant to resume normal message generation and provide a
            // response
            // based on the tool output instead of redundantly triggering it again.

            // Watsonx doesn't return "tool_calls" when the tool-choice is set to REQUIRED.
            context.put(FINISH_REASON_CONTEXT, "tool_calls");

            if (messages.size() > 1) {
                int LAST_MESSAGE = chatRequest.messages().size() - 1;
                ChatMessage lastMessage = chatRequest.messages().get(LAST_MESSAGE);
                if (lastMessage instanceof ToolExecutionResultMessage) {
                    tools = null;
                    textChatParameters.cleanToolChoice();
                    context.delete(FINISH_REASON_CONTEXT);
                }
            }
        }

        TextChatRequest request = new TextChatRequest(modelId, spaceId, projectId, messages, tools, textChatParameters);

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

                                    if (message.finishReason() != null && !context.contains(FINISH_REASON_CONTEXT)) {
                                        context.put(FINISH_REASON_CONTEXT, message.finishReason());
                                    }

                                    if (message.delta().role() != null) {
                                        context.put(ROLE_CONTEXT, message.delta().role());
                                    }

                                    if (message.delta().toolCalls() != null) {

                                        StreamingToolFetcher toolFetcher;

                                        // During streaming there is only one element in the tool_calls,
                                        // but the "index" field can be used to understand how many tools need to be
                                        // executed.
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

                                    List<ToolExecutionRequest> toolExecutionRequests;
                                    List<StreamingToolFetcher> tools = context.get(TOOLS_CONTEXT);

                                    toolExecutionRequests = tools.stream()
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

        public WatsonxStreamingChatModel build() {
            return new WatsonxStreamingChatModel(this);
        }
    }
}
