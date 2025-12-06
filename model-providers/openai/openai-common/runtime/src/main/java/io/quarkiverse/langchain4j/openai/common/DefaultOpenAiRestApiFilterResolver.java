package io.quarkiverse.langchain4j.openai.common;

import java.util.Map;
import java.util.Optional;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class DefaultOpenAiRestApiFilterResolver implements RestApiFilterResolver {
    @Override
    public void resolve(Optional<ModelAuthProvider> provider, QuarkusRestClientBuilder restApiBuilder,
            Map<String, String> configProperties) {
        provider.ifPresent(modelAuthProvider -> restApiBuilder
                .register(new OpenAiRestApi.OpenAIRestAPIFilter(modelAuthProvider)));
    }
}
