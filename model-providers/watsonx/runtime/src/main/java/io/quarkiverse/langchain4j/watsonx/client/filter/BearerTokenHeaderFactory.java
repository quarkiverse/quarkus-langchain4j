package io.quarkiverse.langchain4j.watsonx.client.filter;

import java.util.function.Function;

import jakarta.ws.rs.core.MultivaluedMap;

import io.quarkiverse.langchain4j.watsonx.WatsonxTokenGenerator;
import io.quarkus.rest.client.reactive.ReactiveClientHeadersFactory;
import io.smallrye.mutiny.Uni;

/**
 * Add the bearer token to the watsonx.ai APIs.
 */
public class BearerTokenHeaderFactory extends ReactiveClientHeadersFactory {

    private final WatsonxTokenGenerator tokenGenerator;

    public BearerTokenHeaderFactory(WatsonxTokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Uni<MultivaluedMap<String, String>> getHeaders(MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {

        return tokenGenerator.generate()
                .onItem()
                .transform(new Function<String, String>() {
                    @Override
                    public String apply(String token) {
                        return "Bearer %s".formatted(token);
                    }
                })
                .map(new Function<String, MultivaluedMap<String, String>>() {
                    @Override
                    public MultivaluedMap<String, String> apply(String token) {
                        clientOutgoingHeaders.add("Authorization", token);
                        return clientOutgoingHeaders;
                    }
                });
    }
}
