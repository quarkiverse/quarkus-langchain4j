package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;

final class ToolExecutionResultMessageUtil {
    private ToolExecutionResultMessageUtil() {
    }

    static ToolExecutionResultMessage from(ToolExecutionRequest request, ToolExecutionResult result) {
        return ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                .contents(List.of(TextContent.from(ensureNotNull(result.resultText(), "text"))))
                .isError(result.isError())
                .attributes(result.attributes())
                .build();
    }
}
