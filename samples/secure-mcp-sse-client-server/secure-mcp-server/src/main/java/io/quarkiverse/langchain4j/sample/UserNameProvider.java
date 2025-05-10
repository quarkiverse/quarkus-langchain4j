package io.quarkiverse.langchain4j.sample;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;

public class UserNameProvider {

    @Inject
    SecurityIdentity identity;
    
    @Authenticated
    @Tool(name = "user-name-provider", description = "Provides a name of the currently logged-in user")
    TextContent provideUserName() {
        return new TextContent(identity.getPrincipal().getName());
    }
}
