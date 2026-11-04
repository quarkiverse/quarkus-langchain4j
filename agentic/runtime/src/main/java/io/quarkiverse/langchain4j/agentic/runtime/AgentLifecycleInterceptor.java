package io.quarkiverse.langchain4j.agentic.runtime;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Evicts, when a bean instance of a class-based agent with a scope other than the application scope is destroyed,
 * the agentic scope the instance used and the chat memories created for it.
 */
@AgentLifecycle
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE)
public class AgentLifecycleInterceptor {

    @PreDestroy
    void destroyed(InvocationContext context) throws Exception {
        try {
            context.proceed();
        } finally {
            AgentClassSupport.destroyed(context.getTarget());
        }
    }
}
