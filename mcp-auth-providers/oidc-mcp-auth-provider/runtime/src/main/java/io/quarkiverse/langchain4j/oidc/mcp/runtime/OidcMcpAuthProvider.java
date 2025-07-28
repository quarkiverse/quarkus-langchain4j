package io.quarkiverse.langchain4j.oidc.mcp.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.credential.TokenCredential;

public class OidcMcpAuthProvider implements McpClientAuthProvider {
    private static final Logger log = Logger.getLogger(OidcMcpAuthProvider.class);

    @Override
    public String getAuthorization(Input input) {
        ManagedContext managedContext = Arc.container().requestContext();
        if (managedContext.isActive()) {
            InjectableInstance<TokenCredential> tokenCredential = Arc.container().select(TokenCredential.class);
            if (tokenCredential.isResolvable()) {
                log.debug("Providing the current access token as a bearer access token");
                return "Bearer " + tokenCredential.get().getToken();
            } else {
                log.debug("Access token is not available");
            }
        } else {
            log.debug("Access token can not be detected because the request context is not active");
        }
        return null;
    }
}
