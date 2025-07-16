package io.quarkiverse.langchain4j.oidc.client.mcp.runtime;

import jakarta.inject.Inject;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.runtime.AbstractTokensProducer;
import io.quarkus.oidc.client.runtime.TokensHelper;

public class OidcClientMcpAuthProvider extends AbstractTokensProducer implements McpClientAuthProvider {

    @Inject
    OidcClient client;
    TokensHelper tokens = new TokensHelper();

    @Override
    protected void initTokens() {
        // Avoid early token acquisition
    }

    @Override
    public String getAuthorization(Input input) {
        return "Bearer " + tokens.getTokens(client).await().indefinitely().getAccessToken();
    }
}
