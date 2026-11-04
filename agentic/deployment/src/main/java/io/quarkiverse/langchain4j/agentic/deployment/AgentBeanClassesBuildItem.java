package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.Set;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The agent classes that are CDI beans.
 *
 * @param classBasedAgents the class-based agents: the classes whose agentic method is {@code native}
 * @param instanceAgentBeans the classes with an instance {@code @Agent} or {@code @HumanInTheLoop} method with a
 *        body, non-AI and human-in-the-loop agents invoked on a bean instance
 */
public final class AgentBeanClassesBuildItem extends SimpleBuildItem {

    private final Set<DotName> classBasedAgents;
    private final Set<DotName> instanceAgentBeans;

    public AgentBeanClassesBuildItem(Set<DotName> classBasedAgents, Set<DotName> instanceAgentBeans) {
        this.classBasedAgents = Set.copyOf(classBasedAgents);
        this.instanceAgentBeans = Set.copyOf(instanceAgentBeans);
    }

    public Set<DotName> getClassBasedAgents() {
        return classBasedAgents;
    }

    public Set<DotName> getInstanceAgentBeans() {
        return instanceAgentBeans;
    }

    public boolean isAgentBean(DotName className) {
        return classBasedAgents.contains(className) || instanceAgentBeans.contains(className);
    }
}
