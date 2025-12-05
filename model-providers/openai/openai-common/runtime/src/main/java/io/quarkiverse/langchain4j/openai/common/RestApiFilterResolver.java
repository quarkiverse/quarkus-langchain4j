package io.quarkiverse.langchain4j.openai.common;

import java.util.Optional;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public interface RestApiFilterResolver {
    void resolve(Optional<ModelAuthProvider> provider, QuarkusRestClientBuilder restApiBuilder);
}
