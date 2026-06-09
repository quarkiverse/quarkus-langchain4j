package io.quarkiverse.langchain4j.agentic.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class AgentConfigKeyBuildItem extends MultiBuildItem {

    private final String agentClassName;
    private final String configKey;

    public AgentConfigKeyBuildItem(String agentClassName, String configKey) {
        this.agentClassName = agentClassName;
        this.configKey = configKey;
    }

    public String getAgentClassName() {
        return agentClassName;
    }

    public String getConfigKey() {
        return configKey;
    }
}
