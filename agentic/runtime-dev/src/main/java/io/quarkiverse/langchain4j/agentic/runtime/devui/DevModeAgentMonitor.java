package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Singleton;

import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;

/**
 * Dev-only CDI {@link AgentMonitor} wired into every agent through the additive AgentListener
 * mechanism, capturing invocations so the Dev UI Executions page can render them.
 * <p>
 * A single monitor is shared across every agentic system, so it also records which root agent
 * started each run: when the Dev UI invokes a root, {@link #markPendingRoot(String)} tags the
 * run's memoryId (captured from the first invocation on the calling thread) with that root.
 * This attributes executions to the correct root even when two systems share a sub-agent type.
 */
@Singleton
public class DevModeAgentMonitor extends AgentMonitor {

    private final ThreadLocal<String> pendingRootClassName = new ThreadLocal<>();
    private final Map<Object, String> rootClassNameByMemoryId = new ConcurrentHashMap<>();

    public void markPendingRoot(String rootClassName) {
        pendingRootClassName.set(rootClassName);
    }

    public void clearPendingRoot() {
        pendingRootClassName.remove();
    }

    public String rootClassNameFor(Object memoryId) {
        return rootClassNameByMemoryId.get(memoryId);
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        String pending = pendingRootClassName.get();
        if (pending != null && request.agenticScope() != null && request.agenticScope().memoryId() != null) {
            rootClassNameByMemoryId.putIfAbsent(request.agenticScope().memoryId(), pending);
        }
        super.beforeAgentInvocation(request);
    }
}
