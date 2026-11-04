package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import io.quarkus.arc.Arc;

/**
 * The helper object giving the {@code AgentListener} beans to the agents that langchain4j-agentic builds without
 * calling the configurator of the extension: the static non-AI and human-in-the-loop agents, the A2A and MCP client
 * agents, and the beans with an instance agent method. langchain4j-agentic finds the supplier method by reflection and
 * invokes it when it builds each of those agents, so the method and its class are public, and the class is not final.
 */
public class AgentListenerBeansHelper {

    static final AgentListenerBeansHelper INSTANCE = new AgentListenerBeansHelper();

    /**
     * @return a new listener composed of the {@code AgentListener} beans, never a shared one: langchain4j-agentic adds
     *         the listeners registered later on the same agent to a composed listener in place
     */
    @AgentListenerSupplier
    public AgentListener agentListenerBeans() {
        List<AgentListener> listeners = new ArrayList<>();
        for (AgentListener listener : Arc.container().select(AgentListener.class)) {
            listeners.add(listener);
        }
        return new ComposedAgentListener(listeners.toArray(new AgentListener[0]));
    }
}
