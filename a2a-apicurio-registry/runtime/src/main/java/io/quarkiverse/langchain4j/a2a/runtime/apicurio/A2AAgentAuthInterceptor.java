package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.HashMap;
import java.util.Map;

import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Adds the {@code Authorization} header from an {@link A2AAgentAuthProvider} to each request.
 */
final class A2AAgentAuthInterceptor extends ClientCallInterceptor {

    private final A2AAgentAuthProvider provider;
    private final A2AAgentAuthProvider.DiscoveredAgent agent;

    A2AAgentAuthInterceptor(A2AAgentAuthProvider provider, A2AAgentAuthProvider.DiscoveredAgent agent) {
        this.provider = provider;
        this.agent = agent;
    }

    @Override
    public PayloadAndHeaders intercept(String methodName, Object payload, Map<String, String> headers,
            AgentCard agentCard, ClientCallContext clientCallContext) {
        String authorization = provider.getAuthorization(agent);
        if (authorization == null) {
            return new PayloadAndHeaders(payload, headers);
        }
        Map<String, String> withAuthorization = headers == null ? new HashMap<>() : new HashMap<>(headers);
        withAuthorization.put("Authorization", authorization);
        return new PayloadAndHeaders(payload, withAuthorization);
    }
}
