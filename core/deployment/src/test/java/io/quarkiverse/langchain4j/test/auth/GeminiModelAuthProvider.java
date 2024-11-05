package io.quarkiverse.langchain4j.test.auth;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

@ModelName("gemini")
@ApplicationScoped
public class GeminiModelAuthProvider implements ModelAuthProvider {

    @Override
    public String getAuthorization(Input input) {
        return null;
    }

}
