package io.quarkiverse.langchain4j.data;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.output.TokenUsage;

public class AiStatsMessage extends AiMessage {
    private String updatableText;

    final TokenUsage tokenUsage;

    public AiStatsMessage(String text, TokenUsage tokenUsage) {
        super(text);
        this.updatableText = text;
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
    }

    AiStatsMessage(List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
        super(toolExecutionRequests);
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
    }

    AiStatsMessage(String text, List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
        super(text, toolExecutionRequests);
        this.updatableText = text;
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokenUsage");
    }

    public void updateText(String text) {
        this.updatableText = text;
    }

    @Override
    public String text() {
        return updatableText;
    }

    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public static AiStatsMessage from(AiMessage aiMessage, TokenUsage tokenUsage) {
        if (aiMessage.text() == null) {
            return new AiStatsMessage(aiMessage.toolExecutionRequests(), tokenUsage);
        } else if (aiMessage.hasToolExecutionRequests()) {
            return new AiStatsMessage(aiMessage.text(), aiMessage.toolExecutionRequests(), tokenUsage);
        } else {
            return new AiStatsMessage(aiMessage.text(), tokenUsage);
        }
    }
}
