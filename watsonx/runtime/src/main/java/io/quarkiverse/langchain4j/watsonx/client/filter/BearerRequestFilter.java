package io.quarkiverse.langchain4j.watsonx.client.filter;

import java.io.IOException;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import io.quarkiverse.langchain4j.watsonx.TokenGenerator;

public class BearerRequestFilter implements ClientRequestFilter {

    private final TokenGenerator tokenGenerator;

    public BearerRequestFilter(TokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        String token = tokenGenerator.generate();
        requestContext.getHeaders().putSingle("Authorization", "Bearer %s".formatted(token));
    }
}
