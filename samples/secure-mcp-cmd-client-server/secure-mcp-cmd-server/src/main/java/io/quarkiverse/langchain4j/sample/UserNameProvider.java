package io.quarkiverse.langchain4j.sample;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import jakarta.inject.Inject;

public class UserNameProvider {

    @RestClient
    @Inject
    UserNameRestClient userNameRestClient;

    @Tool(name = "user-name-provider", description = "Provides a name of the currently logged-in user")
    TextContent provideUserName() {
        return new TextContent(userNameRestClient.getUserName());
    }
}
