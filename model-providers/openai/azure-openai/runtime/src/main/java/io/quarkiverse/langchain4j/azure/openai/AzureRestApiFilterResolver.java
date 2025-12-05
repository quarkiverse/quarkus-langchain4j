package io.quarkiverse.langchain4j.azure.openai;

import java.util.Optional;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.openai.common.OpenAiRestApi;
import io.quarkiverse.langchain4j.openai.common.RestApiFilterResolver;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class AzureRestApiFilterResolver implements RestApiFilterResolver {
    @Override
    public void resolve(Optional<ModelAuthProvider> provider, QuarkusRestClientBuilder restApiBuilder) {
        if (provider.isPresent()) {
            restApiBuilder.register(new OpenAiRestApi.OpenAIRestAPIFilter(provider.get()));
        } else {
            restApiBuilder.register(new AzureModelAuthProviderFilter());
        }
    }
}
