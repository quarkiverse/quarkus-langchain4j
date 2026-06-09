package io.quarkiverse.langchain4j.agentic.runtime;

public record AgentClassCreateInfo(String implClassName) {

    public static Object resolveAgentProxy(Object result, Object self, Object agentProxy) {
        return (result == self && agentProxy != null) ? agentProxy : result;
    }
}
