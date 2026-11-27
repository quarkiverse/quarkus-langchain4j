package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import java.util.Map;

import dev.langchain4j.agentic.a2a.A2AClientConfigurator;
import dev.langchain4j.agentic.a2a.A2AClientContext;
import io.quarkiverse.langchain4j.agentic.auth.AgentAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

/**
 * Adds the {@code Authorization} header of the application's {@link AgentAuthProvider}, if it declares one, to the
 * Agent Card request and to every message of each A2A client agent.
 * <p>
 * LangChain4j loads it with {@link java.util.ServiceLoader} once per JVM, possibly before the CDI container starts, so
 * the provider is looked up for each request: this also follows the application across dev mode restarts.
 */
public final class AgentAuthClientConfigurator implements A2AClientConfigurator {

    private static final String AUTHORIZATION = "Authorization";

    @Override
    public Map<String, String> agentCardHeaders(A2AClientContext context) {
        // The Agent Card, and so the remote agent's name, is not known yet: the local agent's name is passed instead
        return authorization(context.agentName(), context.a2aServerUrl());
    }

    @Override
    public Map<String, String> messageHeaders(A2AClientContext context) {
        String agentName = context.agentCard() != null ? context.agentCard().name() : context.agentName();
        return authorization(agentName, context.a2aServerUrl());
    }

    private static Map<String, String> authorization(String agentName, String agentUrl) {
        ArcContainer container = Arc.container();
        if (container == null || !container.isRunning()) {
            return Map.of();
        }
        try (InstanceHandle<AgentAuthProvider> provider = container.instance(AgentAuthProvider.class)) {
            if (!provider.isAvailable()) {
                return Map.of();
            }
            String authorization = provider.get().getAuthorization(new AgentInput(agentName, agentUrl));
            return authorization == null ? Map.of() : Map.of(AUTHORIZATION, authorization);
        }
    }

    private record AgentInput(String agentName, String agentUrl) implements AgentAuthProvider.Input {
    }
}
