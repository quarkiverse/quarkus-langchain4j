package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.Map;

import jakarta.enterprise.context.spi.CreationalContext;

import io.quarkus.arc.BeanDestroyer;

/**
 * Destroys a bean instance of an agent interface whose scope is not the application scope: evicts the agentic scope
 * of the instance and the chat memories created for it.
 */
public class AgentBeanDestroyer implements BeanDestroyer<Object> {

    @Override
    public void destroy(Object instance, CreationalContext<Object> creationalContext, Map<String, Object> params) {
        if (instance instanceof AbstractQuarkusAgent agent && agent.instanceMemoryId() != null) {
            AgentClassSupport.delete(agent, agent.instanceMemoryId());
        }
    }
}
