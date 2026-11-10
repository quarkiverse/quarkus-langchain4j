package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import jakarta.inject.Singleton;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * Builds the card of the exposed agent: first from the configuration, then from the annotation.
 */
@Singleton
public class AgentCardProducer {

    private final ConfigCardBuilderCustomizer configCustomizer;

    public AgentCardProducer(ConfigCardBuilderCustomizer configCustomizer) {
        this.configCustomizer = configCustomizer;
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
        return builder.build();
    }
}
