package io.quarkiverse.langchain4j.agentic.deployment.devui;

import java.util.List;

public class AgentInfo {

    private final String className;
    private final String simpleName;
    private final String agentType;
    private final String description;
    private final String outputKey;
    private final List<String> subAgents;
    private final List<String> methods;
    private final List<String> configAnnotations;
    private final boolean rootAgent;

    public AgentInfo(String className, String simpleName, String agentType, String description,
            String outputKey, List<String> subAgents, List<String> methods,
            List<String> configAnnotations, boolean rootAgent) {
        this.className = className;
        this.simpleName = simpleName;
        this.agentType = agentType;
        this.description = description;
        this.outputKey = outputKey;
        this.subAgents = subAgents;
        this.methods = methods;
        this.configAnnotations = configAnnotations;
        this.rootAgent = rootAgent;
    }

    public String getClassName() {
        return className;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getAgentType() {
        return agentType;
    }

    public String getDescription() {
        return description;
    }

    public String getOutputKey() {
        return outputKey;
    }

    public List<String> getSubAgents() {
        return subAgents;
    }

    public List<String> getMethods() {
        return methods;
    }

    public List<String> getConfigAnnotations() {
        return configAnnotations;
    }

    public boolean isRootAgent() {
        return rootAgent;
    }
}
