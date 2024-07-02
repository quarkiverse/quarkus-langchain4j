package io.quarkiverse.langchain4j.oidc.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.langchain4j.runtime.auth.ModelAuthProvider;
import io.quarkus.security.credential.TokenCredential;

public class OidcModelAuthProvider implements ModelAuthProvider {
    @Inject
    Instance<TokenCredential> tokenCredential;

    @Override
    public String getAuthorization(Input input) {
        return tokenCredential.isResolvable() ? "Bearer " + tokenCredential.get().getToken() : null;
    }
}
