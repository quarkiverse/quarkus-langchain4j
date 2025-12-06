package io.quarkiverse.langchain4j.azure.openai;

import java.net.URI;
import java.util.concurrent.Executor;

import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.smallrye.mutiny.infrastructure.Infrastructure;

public class AzureModelAuthProviderFilter implements ResteasyReactiveClientRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AzureModelAuthProviderFilter.class);
    private final ModelAuthProvider authorizer;

    public AzureModelAuthProviderFilter() {
        this.authorizer = new ApplicationDefaultAuthProvider();
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
        private static final String SCOPE = "https://cognitiveservices.azure.com/.default";
        private static final DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
        private static final TokenRequestContext context = new TokenRequestContext().addScopes(SCOPE);
        private static volatile AccessToken currentToken;

        @Override
        public String getAuthorization(Input input) {
            AccessToken token = currentToken;
            if (token == null || token.isExpired()) {
                synchronized (ApplicationDefaultAuthProvider.class) {
                    token = currentToken;
                    if (token == null || token.isExpired()) {
                        currentToken = credential.getTokenSync(context);
                    }
                }
            }
            return "Bearer " + currentToken.getToken();
        }
    }
}
