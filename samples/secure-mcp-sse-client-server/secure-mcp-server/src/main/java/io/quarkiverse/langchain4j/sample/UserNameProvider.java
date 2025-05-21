package io.quarkiverse.langchain4j.sample;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;

public class UserNameProvider {

    @Inject
    SecurityIdentity securityIdentity;
    
    @PermissionsAllowed("read:name")
    @Tool(name = "user-name-provider", description = "Provides a name of the currently logged-in user")
    TextContent provideUserName() {
        return new TextContent(securityIdentity.getPrincipal().getName());
    }
}
