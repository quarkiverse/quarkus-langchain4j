package io.quarkiverse.langchain4j.mockapi.internal;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.vertexai.runtime.VertxAiRestApi;
import io.quarkus.arc.DefaultBean;

/**
 * Gives the Vertex AI client a fixed access token for the mock, so it never looks up Google Application Default
 * Credentials. A default bean with a priority above the provider's own {@code ApplicationDefaultAuthProvider}, so an
 * application's own {@code AuthProvider} still wins. Registered only when the Vertex AI provider is present.
 */
@Singleton
@DefaultBean
@Priority(1)
public class MockVertexAiAuthProvider implements VertxAiRestApi.AuthProvider {

    @Override
    public String getBearerToken() {
        return ProviderSpecs.GOOGLE_ACCESS_TOKEN;
    }
}
