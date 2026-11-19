package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import java.lang.reflect.Method;
import java.util.Optional;

import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;

/**
 * Wraps the {@link A2AService}, through which LangChain4j creates every A2A client agent, so that their clients send
 * the credentials of the application's {@link io.quarkiverse.langchain4j.agentic.auth.AgentAuthProvider}.
 */
public final class AuthenticatingA2AService implements A2AService {

    private final A2AService delegate;
    private final A2AAgentAuth auth;

    private AuthenticatingA2AService(A2AService delegate, A2AAgentAuth auth) {
        this.delegate = delegate;
        this.auth = auth;
    }

    /**
     * Configures the {@link A2AService} for the starting application. {@link A2AService} is a static singleton, and
     * LangChain4j is not reloaded in dev mode, so the wrapper of a previous start is always removed first.
     *
     * @param auth the credentials of the application
     */
    public static void configure(A2AAgentAuth auth) {
        A2AService service = A2AService.get();
        if (service instanceof AuthenticatingA2AService previous) {
            service = previous.delegate;
        }
        A2AService.setA2AService(auth.isEnabled() ? new AuthenticatingA2AService(service, auth) : service);
    }

    @Override
    public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        return a2aBuilder(a2aServerUrl, null, agentServiceClass);
    }

    @Override
    public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, String tenant, Class<T> agentServiceClass) {
        return new AuthenticatingA2AClientBuilder<>(delegate.a2aBuilder(a2aServerUrl, tenant, agentServiceClass),
                auth.interceptor(a2aServerUrl));
    }

    @Override
    public Optional<AgentExecutor> methodToAgentExecutor(InternalAgent a2aClient, Method method) {
        return delegate.methodToAgentExecutor(a2aClient, method);
    }
}
