package io.quarkiverse.langchain4j.ollama.tool;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.output.TokenUsage;

class AiStatsMessage extends AiMessage {

    final TokenUsage tokenUsage;

    AiStatsMessage(String text, TokenUsage tokenUsage) {
        super(text);
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
    }

    AiStatsMessage(List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
        super(toolExecutionRequests);
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
    }

    AiStatsMessage(String text, List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
        super(text, toolExecutionRequests);
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokenUsage");
    }

    TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    static AiStatsMessage from(AiMessage aiMessage, TokenUsage tokenUsage) {
        if (aiMessage.text() == null) {
            return new AiStatsMessage(aiMessage.toolExecutionRequests(), tokenUsage);
        } else if (aiMessage.hasToolExecutionRequests()) {
            return new AiStatsMessage(aiMessage.text(), aiMessage.toolExecutionRequests(), tokenUsage);
        } else {
            return new AiStatsMessage(aiMessage.text(), tokenUsage);
        }
    }
}
