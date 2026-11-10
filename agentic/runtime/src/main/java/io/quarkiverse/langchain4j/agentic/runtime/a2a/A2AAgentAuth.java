package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;

import io.quarkiverse.langchain4j.agentic.auth.AgentAuthProvider;

/**
 * Creates the interceptor that adds the credentials of the application's {@link AgentAuthProvider}, if it declares one,
 * to the requests to an A2A agent.
 */
public class A2AAgentAuth {

    private final Instance<AgentAuthProvider> provider;

    public A2AAgentAuth(Instance<AgentAuthProvider> provider) {
        this.provider = provider;
    }

    boolean isEnabled() {
        return provider.isResolvable();
    }

    ClientCallInterceptor interceptor(String agentUrl) {
        return new AgentAuthInterceptor(provider.get(), agentUrl);
    }
}
