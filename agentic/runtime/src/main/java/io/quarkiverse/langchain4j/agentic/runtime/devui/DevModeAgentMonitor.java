package io.quarkiverse.langchain4j.agentic.runtime.devui;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.planner.AgentInstance;

/**
 * {@link AgentMonitor} attached by the Dev UI at the root of an agentic system that does not expose its own monitor.
 * <p>
 * Sub-agents resolved as CDI beans are shared between agentic systems, so they also notify the monitors inherited
 * from other roots. Those foreign invocations are ignored instead of starting a bogus execution or failing on a
 * parent this monitor never saw. An {@code async} sub-agent can also start or finish after its parent completed on
 * another thread, so an invocation whose parent is gone by then is dropped as well.
 */
public class DevModeAgentMonitor extends AgentMonitor {

    private static final Logger log = Logger.getLogger(DevModeAgentMonitor.class);

    private final String rootAgentId;

    public DevModeAgentMonitor(AgentInstance rootAgent) {
        this.rootAgentId = rootAgent.agentId();
        setRootAgent(rootAgent);
    }

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        MonitoredExecution execution = ongoingExecutionFor(agentRequest.agenticScope().memoryId());
        if (execution == null) {
            if (!rootAgentId.equals(agentRequest.agentId())) {
                return;
            }
        } else if (agentRequest.agent().parent() == null
                || !isOngoing(execution, agentRequest.agent().parent().agentId())) {
            return;
        }
        try {
            super.beforeAgentInvocation(agentRequest);
        } catch (IllegalStateException e) {
            log.debugf("Dropping agent invocation %s from the Dev UI: %s", agentRequest.agentId(), e.getMessage());
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        if (isTracked(agentResponse.agenticScope().memoryId(), agentResponse.agentId())) {
            try {
                super.afterAgentInvocation(agentResponse);
            } catch (IllegalStateException e) {
                log.debugf("Dropping agent response %s from the Dev UI: %s", agentResponse.agentId(), e.getMessage());
            }
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        if (isTracked(agentInvocationError.agenticScope().memoryId(), agentInvocationError.agentId())) {
            super.onAgentInvocationError(agentInvocationError);
        }
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        if (isTracked(afterAgentToolExecution.agenticScope().memoryId(),
                afterAgentToolExecution.agentInstance().agentId())) {
            super.afterAgentToolExecution(afterAgentToolExecution);
        }
    }

    private boolean isTracked(Object memoryId, String agentId) {
        MonitoredExecution execution = ongoingExecutionFor(memoryId);
        return execution != null && isOngoing(execution, agentId);
    }

    private static boolean isOngoing(MonitoredExecution execution, String agentId) {
        for (AgentInvocation invocation : execution.ongoingInvocations()) {
            if (agentId.equals(invocation.agent().agentId())) {
                return true;
            }
        }
        return false;
    }
}
