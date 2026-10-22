package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.spec.AgentCard;

import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;

/**
 * Builds the card of the exposed agent: first from the configuration, then from the annotation, and last through
 * the application's {@link AgentCardBuilderCustomizer}s, so that any of those can override what came before.
 */
@Singleton
public class AgentCardProducer {

    private final ConfigCardBuilderCustomizer configCustomizer;
    private final Instance<AgentCardBuilderCustomizer> customizers;

    public AgentCardProducer(ConfigCardBuilderCustomizer configCustomizer,
            @Any Instance<AgentCardBuilderCustomizer> customizers) {
        this.configCustomizer = configCustomizer;
        this.customizers = customizers;
    }

    /**
     * Builds the card afresh each time it is resolved, so that it carries the URL known at that moment rather than the
     * one known when it was first built. That is cheap, since the URL is worked out once, when the server starts.
     */
    public AgentCard produce(ExposedAgent agent) {
        AgentCard.Builder builder = AgentCard.builder();
        configCustomizer.customize(builder);
        builder.capabilities(agent.capabilities())
                .defaultInputModes(agent.defaultInputModes())
                .defaultOutputModes(agent.defaultOutputModes())
                .skills(agent.skills());
        for (AgentCardBuilderCustomizer customizer : customizersInDescendingPriorityOrder()) {
            customizer.customize(builder);
        }
        return builder.build();
    }

    private List<AgentCardBuilderCustomizer> customizersInDescendingPriorityOrder() {
        List<AgentCardBuilderCustomizer> applicable = new ArrayList<>();
        for (AgentCardBuilderCustomizer customizer : customizers) {
            applicable.add(customizer);
        }
        Collections.sort(applicable);
        return applicable;
    }
}
