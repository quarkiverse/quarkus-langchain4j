package io.quarkiverse.langchain4j.mockapi.internal;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

/**
 * Authorizes the Vertex AI Gemini requests to the mock with a fixed bearer token, so the client never looks up Google
 * Application Default Credentials. Registered as a synthetic bean per Vertex AI Gemini model (by config name) by the mock's
 * processor.
 */
public class MockVertexAiGeminiAuthProvider implements ModelAuthProvider {

    @Override
    public String getAuthorization(Input input) {
        return "Bearer " + ProviderSpecs.GOOGLE_ACCESS_TOKEN;
    }
}
