package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.net.URI;
import java.util.concurrent.Executor;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.MultivaluedMap;

import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

class McpClientAuthFilter implements ResteasyReactiveClientRequestFilter {
    McpClientAuthProvider authorizer;
    Vertx vertx;

    public McpClientAuthFilter(McpClientAuthProvider authorizer) {
        this.authorizer = authorizer;
        this.vertx = vertx();
    }

    private static Vertx vertx() {
        Instance<Vertx> vertxInstance = CDI.current().select(Vertx.class);
        return vertxInstance.isResolvable() ? vertxInstance.get() : null;
    }

    @Override
    public void filter(ResteasyReactiveClientRequestContext requestContext) {
        if (vertx != null) {
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
        } else {
            setAuthorization(requestContext);
        }

    }

    private Executor createExecutor() {
        Context context = vertx.getOrCreateContext();
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                context.runOnContext(v -> command.run());
            }
        };
    }

    private void setAuthorization(ResteasyReactiveClientRequestContext requestContext) {
        String authValue = authorizer.getAuthorization(new AuthInputImpl(requestContext.getMethod(),
                requestContext.getUri(), requestContext.getHeaders()));
        if (authValue != null) {
            requestContext.getHeaders().putSingle("Authorization", authValue);
        }
    }

    private record AuthInputImpl(
            String method,
            URI uri,
            MultivaluedMap<String, Object> headers) implements McpClientAuthProvider.Input {
    }
}
