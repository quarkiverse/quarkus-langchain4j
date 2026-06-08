package io.quarkiverse.langchain4j.agentic.runtime.observability;

import jakarta.enterprise.util.AnnotationLiteral;

public class AgentSelectorLiteral extends AnnotationLiteral<AgentSelector> implements AgentSelector {

    private final Class<?> agentClass;

    private AgentSelectorLiteral(Class<?> agentClass) {
        this.agentClass = agentClass;
    }

    @Override
    public Class<?> value() {
        return agentClass;
    }

    public static AgentSelectorLiteral of(Class<?> agentClass) {
        return new AgentSelectorLiteral(agentClass);
    }
}
