package io.quarkiverse.langchain4j.oidc.mcp.runtime;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.credential.TokenCredential;

public class OidcMcpAuthProvider implements McpClientAuthProvider {

    @Override
    public String getAuthorization(Input input) {
        ManagedContext managedContext = Arc.container().requestContext();
        if (managedContext.isActive()) {
            InjectableInstance<TokenCredential> tokenCredential = Arc.container().select(TokenCredential.class);
            if (tokenCredential.isResolvable()) {
                return "Bearer " + tokenCredential.get().getToken();
            }
        }
        return null;
    }
}
