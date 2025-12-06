package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCard;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;

public class AgentCardProducer {

    @Produces
    @PublicAgentCard
    public AgentCard agentCard(Instance<AgentCardBuilderCustomizer> customizers) {
        AgentCard.Builder builder = new AgentCard.Builder();
        List<AgentCardBuilderCustomizer> sortedCustomizers = sortCustomizersInDescendingPriorityOrder(customizers);
        for (AgentCardBuilderCustomizer customizer : sortedCustomizers) {
            customizer.customize(builder);
        }
        return builder.build();
    }

    private List<AgentCardBuilderCustomizer> sortCustomizersInDescendingPriorityOrder(
            Iterable<AgentCardBuilderCustomizer> customizers) {
        List<AgentCardBuilderCustomizer> sortedCustomizers = new ArrayList<>();
        for (AgentCardBuilderCustomizer customizer : customizers) {
            sortedCustomizers.add(customizer);
        }
        Collections.sort(sortedCustomizers);
        return sortedCustomizers;
    }
}
