package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.multitenancy.Tenant;
import org.a2aproject.sdk.spec.AgentCard;

import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;

/**
 * Builds the card of each exposed agent: first from the configuration, then from the annotation, and last through
 * the application's {@link AgentCardBuilderCustomizer}s, so that any of those can override what came before.
 * <p>
 * A customizer qualified with {@link Tenant} applies to the card of that tenant alone, and one with no such
 * qualifier to every card.
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
     * Called each time a card is injected rather than once for the application, because the URL it carries is only
     * known once the HTTP server has announced the address it is bound to, and a single shared card would keep
     * whatever was known when it was first built. The SDK's {@code AgentCardCacheMetadata} holds the card the HTTP
     * transports serve, so this stays a handful of builds rather than one per request.
     */
    public AgentCard produce(ExposedAgent agent) {
        AgentCard.Builder builder = AgentCard.builder();
        configCustomizer.customize(builder, agent.tenant());
        builder.capabilities(agent.capabilities())
                .defaultInputModes(agent.defaultInputModes())
                .defaultOutputModes(agent.defaultOutputModes())
                .skills(agent.skills());
        for (AgentCardBuilderCustomizer customizer : customizersInDescendingPriorityOrder(agent.tenant())) {
            customizer.customize(builder);
        }
        return builder.build();
    }

    private List<AgentCardBuilderCustomizer> customizersInDescendingPriorityOrder(String tenant) {
        List<AgentCardBuilderCustomizer> applicable = new ArrayList<>();
        for (Instance.Handle<AgentCardBuilderCustomizer> handle : customizers.handles()) {
            String customizerTenant = tenantOf(handle.getBean().getQualifiers());
            if (customizerTenant == null || customizerTenant.equals(tenant)) {
                applicable.add(handle.get());
            }
        }
        Collections.sort(applicable);
        return applicable;
    }

    private static String tenantOf(Iterable<Annotation> qualifiers) {
        for (Annotation qualifier : qualifiers) {
            if (qualifier instanceof Tenant tenant) {
                return tenant.value();
            }
        }
        return null;
    }
}
