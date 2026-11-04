package io.quarkiverse.langchain4j.agentic.runtime;

import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;

/**
 * Makes the chat memories created during an invocation of an agent bean instance that has its own agentic scope, a
 * class-based agent or an agent interface with a scope other than the application scope, default to the memory id
 * of that instance, so that they are evicted with the instance. It applies before the request-scope based provider
 * of the core extension.
 */
public class AgentInstanceMemoryIdProvider implements DefaultMemoryIdProvider {

    @Override
    public int priority() {
        return DEFAULT_PRIORITY - 100;
    }

    @Override
    public Object getMemoryId() {
        return AgentClassSupport.currentInstanceMemoryId();
    }
}
