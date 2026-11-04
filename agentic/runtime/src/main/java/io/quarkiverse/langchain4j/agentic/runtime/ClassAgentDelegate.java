package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.reflect.Method;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.memory.ChatMemoryAccess;

/**
 * The object a generated subclass of a class-based agent delegates to: it holds the LangChain4j agent and forwards
 * the methods of its interfaces to it, reporting the generated subclass as the agent object, as the generated
 * implementation of an agent interface does by extending {@link AbstractQuarkusAgent} itself. A class-based agent
 * cannot extend both its class and {@link AbstractQuarkusAgent}, hence the delegation. The listener methods of an AI
 * agent, {@code getEventClass} and {@code onEvent}, are inherited with their erased signatures, like the generated
 * implementation of an agent interface inherits them.
 */
public final class ClassAgentDelegate extends AbstractQuarkusAgent implements ChatMemoryAccess, AgenticScopeAccess {

    private final Object owner;
    private final boolean decisionAgent;

    /**
     * @param agent the LangChain4j agent
     * @param owner the generated subclass instance delegating to this object
     * @param decisionAgent whether the agent is a decision agent, which has no agentic scope of its own
     */
    public ClassAgentDelegate(InternalAgent agent, Object owner, boolean decisionAgent) {
        super(agent, owner);
        this.owner = owner;
        this.decisionAgent = decisionAgent;
    }

    /**
     * Invokes the given method of the agent type on the LangChain4j agent.
     */
    public Object invoke(Method method, Object[] args) throws Throwable {
        return invokeAgentMethod(method, args);
    }

    // a decision agent has no scope of its own, and its handler rejects both methods of AgenticScopeOwner

    @Override
    public AgenticScopeOwner withAgenticScope(DefaultAgenticScope scope) {
        return decisionAgent ? (AgenticScopeOwner) owner : super.withAgenticScope(scope);
    }

    @Override
    public AgenticScopeRegistry registry() {
        return decisionAgent ? null : super.registry();
    }

    @Override
    public String toString() {
        return decisionAgent ? agent.toString() : super.toString();
    }

    @Override
    public int hashCode() {
        return decisionAgent ? agent.hashCode() : super.hashCode();
    }
}
