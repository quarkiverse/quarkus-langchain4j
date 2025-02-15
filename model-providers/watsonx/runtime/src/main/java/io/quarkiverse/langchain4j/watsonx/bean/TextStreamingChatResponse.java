package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TextStreamingChatResponse(String id, String modelId, List<TextChatResultChoice> choices, Long created,
        TextChatUsage usage) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextChatResultChoice(Integer index, TextChatResultMessage delta, String finishReason) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextChatUsage(Integer completionTokens, Integer promptTokens, Integer totalTokens) {
        public TokenUsage toTokenUsage() {
            return new TokenUsage(
                    promptTokens,
                    completionTokens,
                    totalTokens);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextChatResultMessage(String role, String content, List<TextChatToolCall> toolCalls) {
    }

    public Long created() {
        if (created != null)
            return TimeUnit.SECONDS.toMillis(created);
        else
            return null;
    }
}
