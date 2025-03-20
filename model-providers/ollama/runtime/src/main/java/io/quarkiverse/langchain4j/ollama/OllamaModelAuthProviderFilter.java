package io.quarkiverse.langchain4j.ollama;

import java.net.URI;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

public class OllamaModelAuthProviderFilter implements ClientRequestFilter {
    private final ModelAuthProvider authorizer;

    public OllamaModelAuthProviderFilter(ModelAuthProvider authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public void filter(ClientRequestContext context) {
        String authValue = authorizer.getAuthorization(new AuthInputImpl(
                context.getMethod(),
                context.getUri(),
                context.getHeaders()));
        if (authValue != null) {
            context.getHeaders().putSingle("Authorization", authValue);
        }
    }

    private record AuthInputImpl(
            String method,
            URI uri,
            MultivaluedMap<String, Object> headers) implements ModelAuthProvider.Input {
    }
}
