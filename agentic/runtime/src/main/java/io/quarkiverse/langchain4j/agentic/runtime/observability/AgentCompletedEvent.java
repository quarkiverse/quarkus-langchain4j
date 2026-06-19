package io.quarkiverse.langchain4j.agentic.runtime.observability;

import java.util.Map;
import java.util.Optional;

import dev.langchain4j.model.output.TokenUsage;

public record AgentCompletedEvent(
        String agentName,
        String agentId,
        Object memoryId,
        Map<String, Object> inputs,
        Object output,
        long durationNanos,
        Optional<TokenUsage> tokenUsage) {
}
