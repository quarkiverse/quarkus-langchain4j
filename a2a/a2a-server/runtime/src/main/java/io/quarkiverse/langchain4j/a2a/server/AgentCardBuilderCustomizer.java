package io.quarkiverse.langchain4j.a2a.server;

import io.a2a.spec.AgentCard;

/**
 * Meant to be implemented by a CDI bean that provides arbitrary customization for {@link AgentCard.Builder}.
 * <p>
 * All implementations (that are registered as CDI beans) are taken into account
 */
public interface AgentCardBuilderCustomizer extends Comparable<AgentCardBuilderCustomizer> {

    int MINIMUM_PRIORITY = Integer.MIN_VALUE;
    int MAXIMUM_PRIORITY = Integer.MAX_VALUE;
    int DEFAULT_PRIORITY = 0;

    void customize(AgentCard.Builder cardBuilder);

    /**
     * Defines the priority that the customizers are applied.
     * A lower integer value means that the customizer will be applied after a customizer with a higher priority
     */
    default int priority() {
        return DEFAULT_PRIORITY;
    }

    default int compareTo(AgentCardBuilderCustomizer o) {
        return Integer.compare(o.priority(), priority());
    }
}
