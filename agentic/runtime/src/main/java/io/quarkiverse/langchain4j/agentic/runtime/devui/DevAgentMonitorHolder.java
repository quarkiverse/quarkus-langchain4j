package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.langchain4j.agentic.observability.AgentMonitor;

public class DevAgentMonitorHolder {

    private static final List<AgentMonitor> MONITORS = new CopyOnWriteArrayList<>();
    private static final List<Object> ROOT_AGENTS = new CopyOnWriteArrayList<>();

    /**
     * Set of agent class names permitted to be invoked via the Dev UI JSON-RPC endpoint.
     * Populated at RUNTIME_INIT by AgenticRecorder.setDevUIAllowedAgentClassNames().
     */
    public static volatile Set<String> allowedAgentClassNames = Collections.emptySet();

    private DevAgentMonitorHolder() {
    }

    public static void register(AgentMonitor monitor) {
        MONITORS.add(monitor);
    }

    public static void registerRootAgent(Object rootAgent) {
        ROOT_AGENTS.add(rootAgent);
    }

    public static List<AgentMonitor> monitors() {
        return MONITORS;
    }

    public static List<Object> rootAgents() {
        return ROOT_AGENTS;
    }

    public static void reset() {
        MONITORS.clear();
        ROOT_AGENTS.clear();
    }
}
