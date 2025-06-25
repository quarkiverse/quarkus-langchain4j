package io.quarkiverse.langchain4j.oidc.mcp.runtime;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.security.credential.TokenCredential;

public class OidcMcpAuthProvider implements McpClientAuthProvider {
    @Inject
    Instance<TokenCredential> tokenCredential;

    @Override
    @ActivateRequestContext
    public String getAuthorization(Input input) {
        return tokenCredential.isResolvable() ? "Bearer " + tokenCredential.get().getToken() : null;
    }
}
