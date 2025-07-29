package io.quarkiverse.langchain4j.gemini.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.concurrent.Executor;

import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;

import com.google.auth.oauth2.GoogleCredentials;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.smallrye.mutiny.infrastructure.Infrastructure;

public class ModelAuthProviderFilter implements ResteasyReactiveClientRequestFilter {

    private final ModelAuthProvider authorizer;

    public ModelAuthProviderFilter(String modelId) {
        this.authorizer = ModelAuthProvider.resolve(modelId).orElse(new ApplicationDefaultAuthProvider());
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

    private record AuthInputImpl(
            String method,
            URI uri,
            MultivaluedMap<String, Object> headers) implements ModelAuthProvider.Input {
    }

    private static class ApplicationDefaultAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            try {
                var credentials = GoogleCredentials.getApplicationDefault();
                credentials.refreshIfExpired();
                return "Bearer " + credentials.getAccessToken().getTokenValue();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
