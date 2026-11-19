package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.planner.AgentInstance;

public class DevAgentMonitorHolder {

    public static volatile Set<String> allowedAgentClassNames = Collections.emptySet();

    private static final List<MonitoredRoot> ROOTS = new CopyOnWriteArrayList<>();

    private DevAgentMonitorHolder() {
    }

    public static void register(AgentInstance rootAgent, AgentMonitor monitor) {
        ROOTS.add(new MonitoredRoot(rootAgent, monitor));
    }

    public static List<MonitoredRoot> roots() {
        return ROOTS;
    }

    public static void reset() {
        ROOTS.clear();
    }

    /**
     * A root agent of an agentic system together with the {@link AgentMonitor} attached at that root.
     */
    public record MonitoredRoot(AgentInstance agent, AgentMonitor monitor) {
    }
}
