package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.net.URI;
import java.util.concurrent.Executor;

import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.smallrye.mutiny.infrastructure.Infrastructure;

class McpClientAuthFilter implements ResteasyReactiveClientRequestFilter {
    McpClientAuthProvider authorizer;

    public McpClientAuthFilter(McpClientAuthProvider authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public void filter(ResteasyReactiveClientRequestContext requestContext) {
        Executor executorService = createExecutor();
        requestContext.suspend();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    setAuthorization(requestContext);
                    requestContext.resume();
                } catch (Exception e) {
                    requestContext.resume(e);
                }
            }
        });
    }

    private Executor createExecutor() {

        InstanceHandle<ManagedExecutor> executor = Arc.container().instance(ManagedExecutor.class);
        return executor.isAvailable() ? executor.get() : Infrastructure.getDefaultExecutor();
    }

    private void setAuthorization(ResteasyReactiveClientRequestContext requestContext) {
        String authValue = authorizer.getAuthorization(new AuthInputImpl(requestContext.getMethod(),
                requestContext.getUri(), requestContext.getHeaders()));
        if (authValue != null) {
            requestContext.getHeaders().putSingle("Authorization", authValue);
        }
    }

    record AuthInputImpl(
            String method,
            URI uri,
            MultivaluedMap<String, Object> headers) implements McpClientAuthProvider.Input {
    }
}
