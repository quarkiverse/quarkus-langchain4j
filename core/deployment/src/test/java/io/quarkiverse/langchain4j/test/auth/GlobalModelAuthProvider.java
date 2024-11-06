package io.quarkiverse.langchain4j.test.auth;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

@ApplicationScoped
public class GlobalModelAuthProvider implements ModelAuthProvider {

    @Override
    public String getAuthorization(Input input) {
        return null;
    }

}
