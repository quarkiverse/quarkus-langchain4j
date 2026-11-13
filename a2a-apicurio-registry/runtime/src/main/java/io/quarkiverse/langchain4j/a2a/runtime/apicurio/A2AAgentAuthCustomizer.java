package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.function.Consumer;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;

/**
 * Configures the client of a discovered agent to send credentials from an {@link A2AAgentAuthProvider}.
 * It replaces the client's default transport, so it registers JSON-RPC itself.
 */
final class A2AAgentAuthCustomizer implements Consumer<ClientBuilder> {

    private final A2AAgentAuthProvider provider;
    private final A2AAgentAuthProvider.DiscoveredAgent agent;

    A2AAgentAuthCustomizer(A2AAgentAuthProvider provider, A2AAgentAuthProvider.DiscoveredAgent agent) {
        this.provider = provider;
        this.agent = agent;
    }

    @Override
    public void accept(ClientBuilder client) {
        client.withTransport(JSONRPCTransport.class,
                new JSONRPCTransportConfigBuilder().addInterceptor(new A2AAgentAuthInterceptor(provider, agent)));
    }
}
