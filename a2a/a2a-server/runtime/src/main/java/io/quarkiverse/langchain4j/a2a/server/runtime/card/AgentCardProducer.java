package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;

import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;

public class AgentCardProducer {

    /**
     * The card is built per injection point rather than once for the application, because the URL it carries is only
     * known once the HTTP server has announced the address it is bound to, and a single shared card would keep
     * whatever was known when it was first built. The SDK's {@code AgentCardCacheMetadata} holds the card the HTTP
     * transports serve, so this stays a handful of builds rather than one per request.
     */
    @Produces
    @PublicAgentCard
    public AgentCard agentCard(Instance<AgentCardBuilderCustomizer> customizers) {
        AgentCard.Builder builder = AgentCard.builder();
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
