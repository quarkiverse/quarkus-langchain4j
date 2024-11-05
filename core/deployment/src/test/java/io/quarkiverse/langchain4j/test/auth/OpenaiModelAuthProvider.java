package io.quarkiverse.langchain4j.test.auth;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

@ModelName("openai")
@ApplicationScoped
public class OpenaiModelAuthProvider implements ModelAuthProvider {

    @Override
    public String getAuthorization(Input input) {
        return null;
    }

}
