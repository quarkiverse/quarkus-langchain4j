package org.acme.example.gemini.aiservices;

import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

@Singleton
public class DummyAuthProvider implements ModelAuthProvider {

    @Override
    public String getAuthorization(Input input) {
        return "Bearer token";
    }

}
