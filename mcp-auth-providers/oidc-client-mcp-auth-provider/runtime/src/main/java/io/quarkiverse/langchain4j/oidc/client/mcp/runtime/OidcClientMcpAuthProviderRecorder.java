package io.quarkiverse.langchain4j.oidc.client.mcp.runtime;

import java.util.function.Function;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OidcClientMcpAuthProviderRecorder {

    public Function<SyntheticCreationalContext<McpClientAuthProvider>, McpClientAuthProvider> defaultProvider() {
        return context -> new OidcClientMcpAuthProvider(context.getInjectedReference(OidcClient.class));
    }

    public Function<SyntheticCreationalContext<McpClientAuthProvider>, McpClientAuthProvider> namedProvider(
            String oidcClientName) {
        return context -> new OidcClientMcpAuthProvider(
                context.getInjectedReference(OidcClients.class).getClient(oidcClientName));
    }
}
