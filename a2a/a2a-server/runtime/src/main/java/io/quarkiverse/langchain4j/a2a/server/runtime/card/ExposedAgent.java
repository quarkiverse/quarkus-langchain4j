package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentSkill;

/**
 * What {@code @ExposeA2AAgent} and the signature of the exposed method say about one agent, read at build time.
 *
 * @param tenant the tenant the agent is exposed under, or {@code null} for the agent served to requests that name
 *        none
 */
public record ExposedAgent(String tenant,
        AgentCapabilities capabilities,
        List<String> defaultInputModes,
        List<String> defaultOutputModes,
        List<AgentSkill> skills) {
}
