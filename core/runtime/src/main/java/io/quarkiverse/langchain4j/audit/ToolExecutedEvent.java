package io.quarkiverse.langchain4j.audit;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * Invoked with a tool response from an LLM. It is important to note that this can be invoked multiple times
 * when tools exist.
 */
public record ToolExecutedEvent(AuditSourceInfo sourceInfo, ToolExecutionRequest request,
        String result) implements LLMInteractionEvent {
}
