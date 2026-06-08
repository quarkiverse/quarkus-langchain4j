package io.quarkiverse.langchain4j.agentic.runtime.observability;

import java.util.Map;

public record AgentErrorEvent(String agentName, String agentId, Object memoryId, Map<String, Object> inputs,
        Throwable error) {
}
