package io.quarkiverse.langchain4j.oidc.client.mcp.runtime;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.runtime.TokensHelper;

public class OidcClientMcpAuthProvider implements McpClientAuthProvider {

    private final OidcClient oidcClient;
    private final TokensHelper tokens = new TokensHelper();

    OidcClientMcpAuthProvider(OidcClient oidcClient) {
        this.oidcClient = oidcClient;
    }

    @Override
    public String getAuthorization(Input input) {
        return "Bearer " + tokens.getTokens(oidcClient).await().indefinitely().getAccessToken();
    }
}
