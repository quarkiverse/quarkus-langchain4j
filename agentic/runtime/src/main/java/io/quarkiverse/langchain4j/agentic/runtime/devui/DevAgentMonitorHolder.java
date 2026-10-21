package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.planner.AgentInstance;

public class DevAgentMonitorHolder {

    public static volatile Set<String> allowedAgentClassNames = Collections.emptySet();

    private static final Map<String, MonitoredRoot> ROOTS = new LinkedHashMap<>();

    private DevAgentMonitorHolder() {
    }

    /**
     * Registers a root agent carrying its own monitor, replacing the previous instance of the same agent class.
     */
    public static synchronized void register(String agentClassName, AgentInstance rootAgent, AgentMonitor monitor) {
        ROOTS.put(agentClassName, new MonitoredRoot(rootAgent, monitor));
    }

    /**
     * The monitor shared by every instance of a root agent class, created and registered with the first instance.
     */
    public static synchronized AgentMonitor sharedMonitor(String agentClassName, AgentInstance rootAgent) {
        MonitoredRoot root = ROOTS.get(agentClassName);
        if (root == null) {
            AgentMonitor monitor = new AgentMonitor();
            monitor.setRootAgent(rootAgent);
            root = new MonitoredRoot(rootAgent, monitor);
            ROOTS.put(agentClassName, root);
        }
        return root.monitor();
    }

    public static synchronized List<MonitoredRoot> roots() {
        return new ArrayList<>(ROOTS.values());
    }

    public static synchronized void reset() {
        ROOTS.clear();
    }

    /**
     * A root agent of an agentic system together with the {@link AgentMonitor} attached at that root.
     */
    public record MonitoredRoot(AgentInstance agent, AgentMonitor monitor) {
    }
}
