package io.quarkiverse.langchain4j.agentic.runtime.observability;

import java.util.Map;

public record AgentStartedEvent(String agentName, String agentId, Object memoryId, Map<String, Object> inputs) {
}
