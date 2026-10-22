package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentSkill;

/**
 * What {@code @ExposeA2AAgent} and the signature of the exposed method say about the agent, read at build time.
 */
public record ExposedAgent(AgentCapabilities capabilities,
        List<String> defaultInputModes,
        List<String> defaultOutputModes,
        List<AgentSkill> skills) {
}
