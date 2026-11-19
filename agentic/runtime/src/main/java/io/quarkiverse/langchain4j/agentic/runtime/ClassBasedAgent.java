package io.quarkiverse.langchain4j.agentic.runtime;

/**
 * Implemented by the generated subclasses of the class-based agents, the objects langchain4j-agentic handles for
 * them.
 */
public interface ClassBasedAgent {

    /**
     * @return the object holding the LangChain4j agent this subclass delegates to
     */
    ClassAgentDelegate agentDelegate();
}
