package io.quarkiverse.langchain4j.watsonx;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTool;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatResultChoice;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatResultMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse.TextChatUsage;

public class WatsonxChatModel extends Watsonx implements ChatLanguageModel {

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

        TextChatParameters textChatParameters = TextChatParameters.convert(parameters);
        TextChatRequest request = new TextChatRequest(modelId, spaceId, projectId, messages, tools, textChatParameters);

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

        public WatsonxChatModel build() {
            return new WatsonxChatModel(this);
        }
    }
}
