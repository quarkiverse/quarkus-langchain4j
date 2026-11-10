package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;

import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.observability.A2AStreamingClientListener;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Configures the client of an A2A client agent with an {@link AgentAuthInterceptor}, before the client customizer of
 * the agent, if it has one.
 */
final class AuthenticatingA2AClientBuilder<T> implements A2AClientBuilder<T> {

    private final A2AClientBuilder<T> delegate;
    private final ClientCallInterceptor authInterceptor;
    private Consumer<ClientBuilder> clientCustomizer;

    AuthenticatingA2AClientBuilder(A2AClientBuilder<T> delegate, ClientCallInterceptor authInterceptor) {
        this.delegate = delegate;
        this.authInterceptor = authInterceptor;
        delegate.clientCustomizer(new AuthCustomizer());
    }

    @Override
    @SuppressWarnings("unchecked")
    public A2AClientBuilder<T> clientCustomizer(Consumer<?> clientCustomizer) {
        if (clientCustomizer != null) {
            this.clientCustomizer = (Consumer<ClientBuilder>) clientCustomizer;
        }
        return this;
    }

    @Override
    public A2AClientBuilder<T> inputKeys(String... inputKeys) {
        delegate.inputKeys(inputKeys);
        return this;
    }

    @Override
    public A2AClientBuilder<T> outputKey(String outputKey) {
        delegate.outputKey(outputKey);
        return this;
    }

    @Override
    public A2AClientBuilder<T> async(boolean async) {
        delegate.async(async);
        return this;
    }

    @Override
    public A2AClientBuilder<T> listener(AgentListener agentListener) {
        delegate.listener(agentListener);
        return this;
    }

    @Override
    public A2AClientBuilder<T> activationCondition(String description, Predicate<AgenticScope> activationCondition) {
        delegate.activationCondition(description, activationCondition);
        return this;
    }

    @Override
    public A2AClientBuilder<T> streamingClientListener(A2AStreamingClientListener<?> clientListener) {
        delegate.streamingClientListener(clientListener);
        return this;
    }

    @Override
    public T build() {
        return delegate.build();
    }

    // Adds the interceptor, then runs the agent's own customizer, if it has one
    private final class AuthCustomizer implements Consumer<ClientBuilder> {

        @Override
        public void accept(ClientBuilder client) {
            client.withTransport(JSONRPCTransport.class,
                    new JSONRPCTransportConfigBuilder().addInterceptor(authInterceptor));
            if (clientCustomizer != null) {
                clientCustomizer.accept(client);
            }
        }
    }
}
