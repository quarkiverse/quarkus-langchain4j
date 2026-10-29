package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.reflect.Type;
import java.util.List;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

/**
 * The base class of the generated implementations of the decision agents, which delegate to the upstream
 * {@code DecisionAgentInvocationHandler}. Unlike {@link AbstractQuarkusAgent}, it implements none of the chat
 * interfaces, whose methods a decision agent rejects.
 */
public abstract class AbstractQuarkusDecisionAgent implements InternalAgent, AgenticScopeOwner {

    protected InternalAgent agent;
    private Object agentProxy;

    protected AbstractQuarkusDecisionAgent() {
    }

    protected AbstractQuarkusDecisionAgent(InternalAgent agent) {
        this.agent = agent;
    }

    public void setAgentProxy(Object proxy) {
        this.agentProxy = proxy;
    }

    // InternalAgent / AgentInstance delegation

    @Override
    public Class<?> type() {
        return agent.type();
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return agent.plannerType();
    }

    @Override
    public String name() {
        return agent.name();
    }

    @Override
    public String agentId() {
        return agent.agentId();
    }

    @Override
    public String description() {
        return agent.description();
    }

    @Override
    public Type outputType() {
        return agent.outputType();
    }

    @Override
    public String outputKey() {
        return agent.outputKey();
    }

    @Override
    public boolean async() {
        return agent.async();
    }

    @Override
    public boolean optional() {
        return agent.optional();
    }

    @Override
    public List<AgentArgument> arguments() {
        return agent.arguments();
    }

    @Override
    public AgentInstance parent() {
        return agent.parent();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agent.subagents();
    }

    @Override
    public AgenticSystemTopology topology() {
        return agent.topology();
    }

    @Override
    public void setParent(InternalAgent parent) {
        agent.setParent(parent);
    }

    @Override
    public void registerInheritedParentListener(AgentListener listener) {
        agent.registerInheritedParentListener(listener);
    }

    @Override
    public void appendId(String suffix) {
        agent.appendId(suffix);
    }

    @Override
    public void setAgentId(String id) {
        agent.setAgentId(id);
    }

    @Override
    public AgentListener listener() {
        return agent.listener();
    }

    @Override
    public boolean allowStreamingOutput() {
        return agent.allowStreamingOutput();
    }

    @Override
    public boolean allowChatMemory() {
        return agent.allowChatMemory();
    }

    @Override
    public <T extends AgentInstance> T as(Class<T> agentInstanceClass) {
        return agent.as(agentInstanceClass);
    }

    // AgenticScopeOwner: the executor of an agentic system calls withAgenticScope on every agent and invokes the
    // agent it returns, which keeps the interception proxy, if any, active inside workflows. A decision agent has no
    // scope of its own, so nothing is forwarded to the handler, which would reject both methods.

    @Override
    public AgenticScopeOwner withAgenticScope(DefaultAgenticScope scope) {
        return agentProxy != null ? (AgenticScopeOwner) agentProxy : this;
    }

    @Override
    public AgenticScopeRegistry registry() {
        return null;
    }

    @Override
    public String toString() {
        return agent == null ? super.toString() : agent.toString();
    }

    @Override
    public int hashCode() {
        return agent == null ? super.hashCode() : agent.hashCode();
    }
}
