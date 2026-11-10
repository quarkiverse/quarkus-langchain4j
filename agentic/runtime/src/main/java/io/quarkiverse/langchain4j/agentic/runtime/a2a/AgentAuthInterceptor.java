package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import java.util.HashMap;
import java.util.Map;

import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.spec.AgentCard;

import io.quarkiverse.langchain4j.agentic.auth.AgentAuthProvider;

/**
 * Adds the {@code Authorization} header from an {@link AgentAuthProvider} to each request to one A2A agent.
 */
final class AgentAuthInterceptor extends ClientCallInterceptor {

    private final AgentAuthProvider provider;
    private final String agentUrl;

    AgentAuthInterceptor(AgentAuthProvider provider, String agentUrl) {
        this.provider = provider;
        this.agentUrl = agentUrl;
    }

    @Override
    public PayloadAndHeaders intercept(String methodName, Object payload, Map<String, String> headers,
            AgentCard agentCard, ClientCallContext clientCallContext) {
        String authorization = provider.getAuthorization(new AgentInput(agentCard.name(), agentUrl));
        if (authorization == null) {
            return new PayloadAndHeaders(payload, headers);
        }
        Map<String, String> withAuthorization = headers == null ? new HashMap<>() : new HashMap<>(headers);
        withAuthorization.put("Authorization", authorization);
        return new PayloadAndHeaders(payload, withAuthorization);
    }

    private record AgentInput(String agentName, String agentUrl) implements AgentAuthProvider.Input {
    }
}
