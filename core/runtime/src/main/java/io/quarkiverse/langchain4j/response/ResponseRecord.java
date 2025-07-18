package io.quarkiverse.langchain4j.response;

import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Abstract away Response vs ChatResponse.
 */
public record ResponseRecord(
        String model,
        AiMessage content,
        TokenUsage tokenUsage,
        FinishReason finishReason,
        Map<String, Object> metadata) {
}
