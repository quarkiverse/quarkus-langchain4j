package io.quarkiverse.langchain4j.runtime.aiservice;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;

final class ToolExecutionResultMessageUtil {
    private ToolExecutionResultMessageUtil() {
    }

    static ToolExecutionResultMessage from(ToolExecutionRequest request, ToolExecutionResult result) {
        return ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                // resultContents() is the canonical accessor: it covers plain text results as well as
                // results carrying images or several content elements. resultText() throws for the latter.
                .contents(result.resultContents())
                .isError(result.isError())
                .attributes(result.attributes())
                .build();
    }
}
