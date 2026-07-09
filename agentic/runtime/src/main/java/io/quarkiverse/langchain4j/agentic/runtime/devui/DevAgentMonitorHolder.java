package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class DevAgentMonitorHolder {

    public static volatile Set<String> allowedAgentClassNames = Collections.emptySet();
    public static volatile Map<String, String> agentTypesByClassName = Collections.emptyMap();

    private static final List<Object> ROOT_AGENTS = new CopyOnWriteArrayList<>();

    private DevAgentMonitorHolder() {
    }

    public static void registerRootAgent(Object rootAgent) {
        ROOT_AGENTS.add(rootAgent);
    }

    public static List<Object> rootAgents() {
        return ROOT_AGENTS;
    }

    public static void reset() {
        ROOT_AGENTS.clear();
    }
}
